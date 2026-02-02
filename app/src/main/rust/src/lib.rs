use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;

#[no_mangle]
pub extern "system" fn Java_com_example_recemotion_MainActivity_helloFromRust(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let output = env.new_string("Hello World from Rust!")
        .expect("Couldn't create java string!");
    output.into_raw()
}
