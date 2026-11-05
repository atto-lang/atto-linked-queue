//! Tests the host-only source contract gate binary, not a queue guest runtime.
use std::{fs, path::Path, process::{Command, Output}};
use tempfile::TempDir;

fn fixture() -> TempDir {
    let temp = tempfile::tempdir().expect("temporary fixture");
    fs::create_dir_all(temp.path().join("src")).unwrap();
    fs::create_dir_all(temp.path().join("tests")).unwrap();
    let actual = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..")
        .join("src/int_linked_queue.ores");
    fs::copy(actual, temp.path().join("src/int_linked_queue.ores")).unwrap();
    temp
}

fn run(root: &Path) -> Output {
    Command::new(env!("CARGO_BIN_EXE_source-contract-check"))
        .arg("--root").arg(root).output().expect("run source policy")
}

fn denial(root: &Path, reason: &str) {
    let output=run(root);
    assert!(!output.status.success(), "denial unexpectedly accepted");
    assert!(String::from_utf8_lossy(&output.stderr).contains(reason),
        "expected {reason:?}, got {:?}", String::from_utf8_lossy(&output.stderr));
}

#[test]
fn valid_tracked_source_and_inert_comment_do_not_create_false_denial() {
    let f=fixture();
    fs::write(f.path().join("tests/comments.ores"),
        "// import X from \"java:lang\"\n// if true; then return 1; fi\n").unwrap();
    let result=run(f.path());
    assert!(result.status.success(), "{}", String::from_utf8_lossy(&result.stderr));
}

#[test]
fn java_foreign_import_and_legacy_conditional_are_independent_denials() {
    let f=fixture();
    let file=f.path().join("tests/negative.ores");
    fs::write(&file, "import X from \"java:lang\";\n").unwrap();
    denial(f.path(), "forbidden Java interop");
    fs::write(&file,"if ready; then return 7; fi\n").unwrap();
    denial(f.path(), "legacy conditional separator");
}

#[test]
fn readonly_queue_owner_is_denied_in_test_sources() {
    let f=fixture();
    fs::write(f.path().join("tests/owner.ores"),
        "let IntLinkedQueue queue = new IntLinkedQueue();\n").unwrap();
    denial(f.path(), "queue test needs a writable val owner");
}

#[test]
fn source_without_final_newline_is_refused_before_certification() {
    let f=fixture();
    fs::write(f.path().join("tests/not-terminated.ores"),"return 1;").unwrap();
    denial(f.path(), "missing final newline");
}

#[test]
fn invalid_utf8_source_is_fail_closed() {
    let f=fixture();
    fs::write(f.path().join("tests/invalid.ores"),[0xff,0xfe,0xfa]).unwrap();
    denial(f.path(), "cannot read UTF-8 source");
}
