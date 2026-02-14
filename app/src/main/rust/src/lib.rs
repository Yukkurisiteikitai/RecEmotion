pub mod core;
pub mod facs; // Expose the facs module so it's compiled

use jni::JNIEnv;
use jni::objects::{JClass, JString, JFloatArray};
use jni::sys::{jlong, jstring, jint, JavaVM};

// ========== Library Load Confirmation ==========
#[no_mangle]
pub extern "system" fn JNI_OnLoad(_vm: *mut JavaVM, _reserved: *mut std::ffi::c_void) -> jint {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("recemotion_rust"),
    );
    log::info!("✅ librecemotion.so JNI_OnLoad called - Rust library loaded successfully");
    jni::sys::JNI_VERSION_1_6
}

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_initSession(
    _env: JNIEnv,
    _class: JClass,
    wake_time: jlong,
) {
    core::init_session(wake_time);
}

// RENAMED from pushEmotionFrame to pushFaceLandmarks
#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_pushFaceLandmarks(
    env: JNIEnv,
    _class: JClass,
    landmarks: JFloatArray,
) {
    if let Ok(len) = env.get_array_length(&landmarks) {
       let mut buf = vec![0.0; len as usize];
       // landmarks array is expected to be flattened [x1, y1, z1, x2, y2, z2, ...]
       if let Ok(_) = env.get_float_array_region(&landmarks, 0, &mut buf) {
           core::process_face_landmarks(buf);
       }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_getAnalysisJson(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    let input_text: String = env.get_string(&text)
        .map(|s| s.into())
        .unwrap_or_else(|_| "Error".to_string());
    
    let json_output = core::generate_analysis_json(input_text);
    
    env.new_string(json_output)
        .expect("Couldn't create java string!")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_updateStressLevel(
    _env: JNIEnv,
    _class: JClass,
    level: jint,
) {
    core::update_stress(level);
}
