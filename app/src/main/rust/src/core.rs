use anyhow::{Result, Context};
use serde::{Deserialize, Serialize};
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Serialize, Deserialize, Debug)]
pub struct Message {
    pub sender: String,
    pub content: Vec<u8>, // Encrypted content
    pub timestamp: u64,
}

/// Mock encryption function.
pub fn encrypt(data: &[u8], _key: &[u8]) -> Result<Vec<u8>> {
    let mut output = Vec::new();
    output.extend_from_slice(b"ENC:");
    output.extend(data.iter().rev());
    Ok(output)
}

/// Mock decryption function.
pub fn decrypt(data: &[u8]) -> Result<Vec<u8>> {
    if data.starts_with(b"ENC:") {
        let content = &data[4..];
        Ok(content.iter().rev().cloned().collect())
    } else {
        Ok(data.to_vec()) // Fallback for testing
    }
}

/// Synchronously send a message to target.
/// This blocks, so it must be run in a background thread on Android.
pub fn send_message_sync(target: &str, sender: &str, content: &[u8]) -> Result<()> {
    let mut stream = TcpStream::connect(target)
        .with_context(|| format!("Failed to connect to {}", target))?;

    let encrypted_content = encrypt(content, &[])?;
    
    let msg = Message {
        sender: sender.to_string(),
        content: encrypted_content,
        timestamp: SystemTime::now().duration_since(UNIX_EPOCH)?.as_secs(),
    };

    let encoded = bincode::serialize(&msg)?;
    
    // Protocol: Length (u32 BE) + Data
    let len = encoded.len() as u32;
    stream.write_all(&len.to_be_bytes())?;
    stream.write_all(&encoded)?;

    Ok(())
}

/// Synchronously start a server to receive messages.
/// This blocks forever.
pub fn start_server_sync(port: u16) -> Result<()> {
    let listener = TcpListener::bind(("0.0.0.0", port))
        .with_context(|| format!("Failed to bind to 0.0.0.0:{}", port))?;

    println!("Server listening on 0.0.0.0:{}", port);

    for stream in listener.incoming() {
        match stream {
            Ok(mut stream) => {
                if let Err(e) = handle_client(&mut stream) {
                    eprintln!("Error handling client: {}", e);
                }
            }
            Err(e) => eprintln!("Connection failed: {}", e),
        }
    }
    Ok(())
}

fn handle_client(stream: &mut TcpStream) -> Result<()> {
    let mut len_buf = [0u8; 4];
    stream.read_exact(&mut len_buf)?;
    let len = u32::from_be_bytes(len_buf) as usize;

    let mut buf = vec![0u8; len];
    stream.read_exact(&mut buf)?;

    let msg: Message = bincode::deserialize(&buf)?;
    let decrypted = decrypt(&msg.content)?;
    
    println!("[{}] {}", msg.sender, String::from_utf8_lossy(&decrypted));
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encrypt_decrypt() {
        let input = b"hello";
        let encrypted = encrypt(input, &[]).unwrap();
        let decrypted = decrypt(&encrypted).unwrap();
        assert_eq!(decrypted, input);
    }
}

use std::sync::Mutex;
use lazy_static::lazy_static;
use crate::facs::{
    landmarks::Point3D,
    features::GeometricFeatureExtractor,
    calibration::StatisticalCalibrator,
    calculate_emotion,
};

// --- State Management ---

#[derive(Debug, Clone)]
pub struct SessionState {
    pub wake_time: Option<i64>,
    pub stress_level: i32,
    pub calibrator: StatisticalCalibrator,
    pub current_emotion: String,
    pub emotion_history: Vec<String>,
}

impl SessionState {
    pub fn new() -> Self {
        Self {
            wake_time: None,
            stress_level: 1,
            calibrator: StatisticalCalibrator::new(),
            current_emotion: "Neutral".to_string(),
            emotion_history: Vec::new(),
        }
    }
}

lazy_static! {
    pub static ref GLOBAL_STATE: Mutex<SessionState> = Mutex::new(SessionState::new());
}

// --- Analysis Logic ---

pub fn init_session(wake_time: i64) {
    let mut state = GLOBAL_STATE.lock().unwrap();
    state.wake_time = Some(wake_time);
    state.calibrator = StatisticalCalibrator::new(); // Reset calibration
    state.emotion_history.clear();
    state.current_emotion = "Neutral".to_string();
    println!("Rust State: Session initialized with wake_time={}", wake_time);
}

pub fn update_stress(level: i32) {
    let mut state = GLOBAL_STATE.lock().unwrap();
    state.stress_level = level.clamp(1, 5);
    println!("Rust State: Stress level updated to {}", state.stress_level);
}

// Replaces push_emotion_frame
pub fn process_face_landmarks(coords: Vec<f32>) {
    let mut state = GLOBAL_STATE.lock().unwrap();
    
    // Convert flat vec to Point3D (r.len() / 3)
    let num_points = coords.len() / 3;
    if num_points < 468 {
        return; // Invalid frame
    }

    let mut landmarks = Vec::with_capacity(num_points);
    for i in 0..num_points {
        landmarks.push(Point3D::new(coords[i*3], coords[i*3+1], coords[i*3+2]));
    }

    let extractor = GeometricFeatureExtractor::new();
    if let Some(features) = extractor.extract(&landmarks) {
        // If not calibrated, use this frame to calibrate
        if !state.calibrator.is_calibrated {
            state.calibrator.add_sample(features);
            
            // Auto-finalize if enough samples (e.g., 30 frames ~ 1 sec)
            if state.calibrator.samples.len() >= 30 {
                state.calibrator.finalize_calibration();
            }
        } else {
            // Already calibrated: calculate Z-scores and emotion
            let mut z_scores = std::collections::HashMap::new();
            for (k, v) in features {
                let z = state.calibrator.get_z_score(&k, v);
                z_scores.insert(k, z);
            }
            
            let emotion = calculate_emotion(&z_scores);
            state.current_emotion = emotion.clone();
            state.emotion_history.push(emotion);
            if state.emotion_history.len() > 100 {
                state.emotion_history.remove(0);
            }
        }
    }
}

pub fn calculate_energy() -> i32 {
    let state = GLOBAL_STATE.lock().unwrap();
    match state.wake_time {
        Some(wake) => {
            let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64;
            calculate_energy_logic(wake, now)
        }
        None => 3, 
    }
}

fn calculate_energy_logic(wake_time: i64, current_time: i64) -> i32 {
    let hours_awake = (current_time - wake_time) as f64 / 3600.0;
    
    if hours_awake < 2.0 { 5 }
    else if hours_awake < 6.0 { 4 }
    else if hours_awake < 10.0 { 3 }
    else if hours_awake < 14.0 { 2 }
    else { 1 }
}

pub fn generate_analysis_json(text_input: String) -> String {
    let state = GLOBAL_STATE.lock().unwrap();
    
    let energy = match state.wake_time {
        Some(w) => calculate_energy_logic(w, SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs() as i64),
        None => 3
    };

    let emotion_summary = if state.emotion_history.is_empty() {
        "Neutral".to_string()
    } else {
        // Simple mode (most frequent emotion)
        use std::collections::HashMap;
        let mut counts = HashMap::new();
        for e in &state.emotion_history {
            *counts.entry(e).or_insert(0) += 1;
        }
        counts.into_iter().max_by_key(|&(_, count)| count).map(|(e, _)| e.clone()).unwrap_or("Neutral".to_string())
    };

    let response = serde_json::json!({
        "status": "success",
        "context": {
            "energy_level": energy,
            "stress_level": state.stress_level,
            "wake_time": state.wake_time.unwrap_or(0),
            "input_text": text_input
        },
        "emotion_data": {
            "current_emotion": state.current_emotion,
            "summary": emotion_summary,
            "is_calibrated": state.calibrator.is_calibrated
        }
    });

    response.to_string()
}
