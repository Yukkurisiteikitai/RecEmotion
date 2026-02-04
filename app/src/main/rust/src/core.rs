// P2P Networking code removed to focus on RecEmotion logic

use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
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

    // 1. Gating: Quality Control (Head Pose)
    if !is_head_pose_valid(&landmarks) {
        // println!("Rust: Frame skipped due to head pose");
        return;
    }

    let extractor = GeometricFeatureExtractor::new();
    if let Some(features) = extractor.extract(&landmarks) {
        // If not calibrated, use this frame to calibrate
        if !state.calibrator.is_calibrated {
            state.calibrator.add_sample(features);
            
            // Auto-finalize if enough samples (e.g., 30 frames ~ 1 sec)
            if state.calibrator.samples.len() >= 30 {
                state.calibrator.finalize_calibration();
                println!("Rust: Calibration finalized!");
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

fn is_head_pose_valid(landmarks: &[Point3D]) -> bool {
    // Simple Yaw Check: |LeftEye.z - RightEye.z| / IPD
    // Indices: Left Eye Corner 33, Right Eye Corner 263
    let left = &landmarks[33];
    let right = &landmarks[263];
    
    let ipd = left.euclidean_dist(right);
    if ipd < 0.1 { return false; } // Too far/small

    let z_diff = (left.z - right.z).abs();
    let yaw_ratio = z_diff / ipd;

    // Threshold ~ 0.2 corresponds to roughly 15-20 degrees
    if yaw_ratio > 0.25 {
        return false;
    }
    true
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

    // Calculate detailed breakdown for "Link to Past Learning"
    // e.g., "Sadness: 20%, Anger: 5%"
    // For now, just sending the summary.

    let response = serde_json::json!({
        "status": "success",
        "timestamp": SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_secs(),
        "context": {
            "energy_level": energy,
            "stress_level": state.stress_level,
            "wake_time": state.wake_time.unwrap_or(0),
            "input_text": text_input
        },
        "emotion_data": {
            "current_emotion": state.current_emotion,
            "dominant_emotion_last_Session": emotion_summary,
            "is_calibrated": state.calibrator.is_calibrated,
            "sample_count": state.emotion_history.len()
        },
        "insight_hints": {
            "has_discrepancy": state.current_emotion != "Neutral" && state.stress_level == 1 // Simple example logic
        }
    });

    response.to_string()
}
