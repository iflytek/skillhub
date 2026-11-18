## 1. Repository read model

- [x] 1.1 Implement paginated repository and member queries over verified source bindings and eligible published versions.
- [x] 1.2 Document and generate the API consumer contract. Verify visibility, renamed repositories, legacy data and stable pagination against PostgreSQL.

## 2. Catalog interaction

- [x] 2.1 Add repository browsing and linkable detail with existing navigation, loading, empty and error patterns.
- [x] 2.2 Verify URL navigation, return paths and responsive behavior in the browser using published and excluded fixtures.

## 3. Snapshot delivery

- [x] 3.1 Capture complete path sets and verify publication, unchanged versions, visibility and historical migration.
- [x] 3.2 Implement snapshot history, exact-version comparison and batch ZIP. Verify desktop, mobile and actual downloaded payloads.
- [ ] 3.3 Define and implement the independent CLI install and upgrade contract, including local ownership, conflict handling and recovery.
