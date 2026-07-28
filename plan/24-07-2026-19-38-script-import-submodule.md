# Script Tự Động Import AI-Coding Repo (Git Submodule)

## Mục tiêu

Viết bộ script đặt trong thư mục `scripts/` của repo **AI-Coding**, cho phép các project khác chạy một lệnh duy nhất để tự động import repo này vào thư mục `.agents/` dưới dạng **Git Submodule**.

## Thông tin xác nhận

- **URL repo**: `https://github.com/phamthanhlong100504-xpnd/doc-manual.git`
- **Branch mặc định**: `main`
- **OS hỗ trợ**: Windows (PowerShell) + Linux/macOS (Bash)

## Proposed Changes

| File | Action | Mô tả |
|------|--------|-------|
| `scripts/setup.sh` | **NEW** | Script setup submodule (Bash) |
| `scripts/setup.ps1` | **NEW** | Script setup submodule (PowerShell) |
| `scripts/update.sh` | **NEW** | Script update submodule (Bash) |
| `scripts/update.ps1` | **NEW** | Script update submodule (PowerShell) |
| `manifest.json` | **MODIFY** | Thêm section `scripts` |
| `README.md` | **MODIFY** | Thêm hướng dẫn cài đặt |
