//! Source-only checker process regression: no queue guest/compiler execution.
use std::{fs, path::Path, process::{Command, Output}};
use tempfile::TempDir;

fn fixture() -> TempDir {
    let temp = tempfile::tempdir().expect("temporary project");
    fs::create_dir_all(temp.path().join("src")).unwrap();
    fs::create_dir_all(temp.path().join("tests")).unwrap();
    let source = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../..").join("src/int_linked_queue.ores");
    fs::copy(source, temp.path().join("src/int_linked_queue.ores")).unwrap();
    temp
}

fn check(root: &Path) -> Output {
    Command::new(env!("CARGO_BIN_EXE_source-contract-check"))
        .arg("--root").arg(root).output().expect("run source contract checker")
}

#[test]
fn inactive_comment_markers_and_empty_files_do_not_spoof_source_diagnostics() {
    let f = fixture();
    fs::write(f.path().join("tests/empty.ores"), "").unwrap();
    fs::write(f.path().join("tests/comments.ores"),
        "  // import X from 'java:lang'\n  // if ready; then return 7; fi\n"
    ).unwrap();
    let result = check(f.path());
    assert!(result.status.success(), "{}", String::from_utf8_lossy(&result.stderr));
}

#[test]
fn tab_indentation_and_compact_conditional_are_both_reported_with_file_and_line() {
    let f = fixture();
    fs::write(f.path().join("tests/two-errors.ores"),
        "\tif ready then return 7 fi\n"
    ).unwrap();
    let result = check(f.path());
    assert!(!result.status.success());
    let stderr = String::from_utf8_lossy(&result.stderr);
    assert!(stderr.contains("tests/two-errors.ores:1: tab indentation"), "{stderr}");
    assert!(stderr.contains("tests/two-errors.ores:1: compact conditional body"), "{stderr}");
}

#[test]
fn nested_active_foreign_import_is_denied_but_unrelated_comments_remain_inert() {
    let f = fixture();
    let nested = f.path().join("tests/deep");
    fs::create_dir_all(&nested).unwrap();
    fs::write(nested.join("negative.ores"),
        "// import Ignored from 'java:lang'\n  import Active from 'java:lang';\n"
    ).unwrap();
    let result = check(f.path());
    assert!(!result.status.success());
    let stderr = String::from_utf8_lossy(&result.stderr);
    assert!(stderr.contains("forbidden Java interop"), "{stderr}");
    assert!(stderr.contains("negative.ores:2"), "{stderr}");
}
