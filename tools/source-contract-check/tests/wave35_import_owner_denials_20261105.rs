//! Host-only adversarial source policy tests; never guest execution evidence.
use std::{
    fs,
    path::Path,
    process::{Command, Output},
};
use tempfile::TempDir;

fn fixture() -> TempDir {
    let temp = tempfile::tempdir().expect("temporary fixture");
    fs::create_dir_all(temp.path().join("src")).unwrap();
    fs::create_dir_all(temp.path().join("tests")).unwrap();
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    fs::copy(
        repo.join("src/int_linked_queue.ores"),
        temp.path().join("src/int_linked_queue.ores"),
    )
    .unwrap();
    temp
}

fn check(root: &Path) -> Output {
    Command::new(env!("CARGO_BIN_EXE_source-contract-check"))
        .arg("--root")
        .arg(root)
        .output()
        .expect("source-contract binary")
}

fn denied(output: Output, reason: &str) {
    assert!(!output.status.success(), "invalid source admitted");
    let stderr = String::from_utf8_lossy(&output.stderr);
    assert!(stderr.contains(reason), "missing {reason:?}: {stderr}");
}

#[test]
fn java_import_in_test_fixture_is_denied_before_guest_execution() {
    let temp = fixture();
    fs::write(
        temp.path().join("tests/foreign.ores"),
        "import Unsafe from \"java:lang\";\n",
    )
    .unwrap();
    denied(check(temp.path()), "forbidden Java interop");
}

#[test]
fn immutable_queue_owner_in_test_fixture_is_denied() {
    let temp = fixture();
    fs::write(
        temp.path().join("tests/immutable.ores"),
        "let IntLinkedQueue queue = IntLinkedQueue();\n",
    )
    .unwrap();
    denied(check(temp.path()), "queue test needs a writable val owner");
}

#[test]
fn missing_mutating_receiver_cannot_be_hidden_by_an_inert_comment() {
    let temp = fixture();
    let path = temp.path().join("src/int_linked_queue.ores");
    let source = fs::read_to_string(&path).unwrap();
    assert!(source.contains("pub enqueue(mut self)("));
    fs::write(
        path,
        source.replacen(
            "pub enqueue(mut self)(",
            "// pub enqueue(mut self)(\n  pub enqueue(",
            1,
        ),
    )
    .unwrap();
    denied(check(temp.path()), "enqueue must appear once with mut self");
}
