pub mod core;

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_helloFromRust(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let input = "Hello Hybrid Rust!";
    let encrypted_res = core::encrypt(input.as_bytes(), &[]);
    
    let output_msg = match encrypted_res {
        Ok(bytes) => format!("Encrypted: {}", String::from_utf8_lossy(&bytes)),
        Err(e) => format!("Error: {}", e),
    };

    let output = env.new_string(output_msg)
        .expect("Couldn't create java string!");
    output.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_sendMessage(
    mut env: JNIEnv,
    _class: JClass,
    target: JString,
    sender: JString,
    message: JString,
) -> jni::sys::jboolean {
    let target_str: String = env.get_string(&target).expect("Invalid target").into();
    let sender_str: String = env.get_string(&sender).expect("Invalid sender").into();
    let message_str: String = env.get_string(&message).expect("Invalid message").into();

    match core::send_message_sync(&target_str, &sender_str, message_str.as_bytes()) {
        Ok(_) => 1, // true
        Err(e) => {
            // Log error? For now just return false
            // android_logger::init_once(...) could be useful but we won't add deps now
            0 // false
        }
    }
}
