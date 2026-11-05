//! Host-only source contract regressions: not executed AttoLang queue semantics.
use std::{fs, path::Path, process::{Command, Output}};
use tempfile::TempDir;

fn setup() -> TempDir {
    let temp = tempfile::tempdir().expect("temp source fixture");
    fs::create_dir_all(temp.path().join("src")).unwrap();
    fs::create_dir_all(temp.path().join("tests")).unwrap();
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    fs::copy(repo.join("src/int_linked_queue.ores"),
             temp.path().join("src/int_linked_queue.ores")).unwrap();
    temp
}
fn check(root: &Path) -> Output {
    Command::new(env!("CARGO_BIN_EXE_source-contract-check"))
        .arg("--root").arg(root).output().expect("host gate executable")
}
fn deny(result: Output, fragment: &str) {
    assert!(!result.status.success(), "unexpected host admission");
    assert!(String::from_utf8_lossy(&result.stderr).contains(fragment),
            "expected {fragment:?}, got {}", String::from_utf8_lossy(&result.stderr));
}

#[test]
fn clean_source_and_inert_comments_are_not_misclassified_as_guest_proof() {
    let t = setup();
    fs::write(t.path().join("tests/comments.ores"),
              "// if ready then return 7; fi\n// import x from \"java:lang\";\n").unwrap();
    let result = check(t.path());
    assert!(result.status.success(), "{}", String::from_utf8_lossy(&result.stderr));
    assert!(String::from_utf8_lossy(&result.stdout).contains("NOT a compilation"));
}
#[test]
fn compact_if_body_is_refused_separately_from_legacy_semicolon() {
    let t = setup();
    fs::write(t.path().join("tests/inline.ores"),
              "if ready then return 7; fi\n").unwrap();
    deny(check(t.path()), "compact conditional body");
}
#[test]
fn tab_indentation_and_nonterminated_guest_fixture_are_independent_denials() {
    let t = setup();
    let file = t.path().join("tests/layout.ores");
    fs::write(&file, "\tif ready { return 1; }\n").unwrap();
    deny(check(t.path()), "tab indentation");
    fs::write(&file, "pub fnc main(): int { return 0; }").unwrap();
    deny(check(t.path()), "missing final newline");
}
#[test]
fn mutated_receiver_and_duplicate_observer_fail_closed() {
    let t = setup();
    let path = t.path().join("src/int_linked_queue.ores");
    let original = fs::read_to_string(&path).unwrap();
    assert!(original.contains("pub enqueue(mut self)("));
    fs::write(&path, original.replacen("pub enqueue(mut self)(",
                                      "pub enqueue(", 1)).unwrap();
    deny(check(t.path()), "enqueue must appear once with mut self");
    let double_reader = original.replacen(
        "pub size() -> int", "pub size() -> int { return 0; }\n  pub size() -> int", 1);
    assert_ne!(double_reader, original);
    fs::write(&path, double_reader).unwrap();
    deny(check(t.path()), "size must appear once without mut self");
}
