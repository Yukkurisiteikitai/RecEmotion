pub mod core;

use jni::JNIEnv;
use jni::objects::{JClass, JString, JFloatArray};
use jni::sys::{jlong, jstring, jint};

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_initSession(
    _env: JNIEnv,
    _class: JClass,
    wake_time: jlong,
) {
    core::init_session(wake_time);
}

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_pushEmotionFrame(
    env: JNIEnv,
    _class: JClass,
    scores: JFloatArray,
) {
    if let Ok(len) = env.get_array_length(&scores) {
       let mut buf = vec![0.0; len as usize];
       if let Ok(_) = env.get_float_array_region(&scores, 0, &mut buf) {
           core::push_emotion_frame(buf);
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
