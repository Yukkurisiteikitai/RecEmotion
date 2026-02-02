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
