# PrivAIBrowser (Android)

A privacy-first Android browser built around **Zero-PII Structure-Only AI Automation**, **Deterministic Local Form Filling**, **Structured Data Extraction**, **In-Browser AI Add-on & Extension Builder**, **Tracker Blocking**, **Background Media Playback**, and a **Native Download Manager** — with automated cloud APK builds via GitHub Actions and a live static download page via GitHub Pages.

## Key Features
- **Zero-PII Structure-Only AI:** Personal data stays in your on-device Local Vault (`MODE_PRIVATE`). Only sanitized DOM schemas (field labels, `id`/`name`, input types, and selectors—never values) are sent to your configured AI endpoint.
- **Deterministic Form Auto-Fill:** AI maps sanitized form schemas to your Local Vault keys; filling happens 100% locally with React/Vue-compatible input/change event dispatch.
- **Structured Extraction:** Extract any webpage table or repeating card structure into JSON or CSV.
- **In-Browser AI Add-on Creator:** Describe an automation or UI tweak in plain language -> AI generates a structured add-on from the page's sanitized DOM -> save and auto-run on matching URLs.
- **General Tracker & Ad-Domain Blocking:** Host-level request filtering for common tracker/ad domains (toggleable in Settings).
- **Background Audio Playback:** Keeps HTML5 media playing when switching apps or locking the screen via a lightweight Android Foreground Service.
- **Native File Download Manager:** Saves direct file downloads to your device's Downloads folder with system notifications.
- **BYO AI Key / Local LLM:** Supports Google Gemini API, OpenAI-compatible APIs, or a local Ollama/LAN endpoint.

## Quick Setup (Zero PC Needed — Live APK via GitHub)
1. Create a new repository on GitHub (e.g. `PrivAIBrowser`) and upload all files from this folder.
2. Go to **Actions** tab -> enable workflows if prompted -> run **Build & Release Android APK** (or it runs automatically on push to `main`).
3. Go to **Settings -> Pages** -> set Source to **Deploy from a branch**, Branch: `main`, Folder: `/docs` -> **Save**.
4. Open `https://<your-username>.github.io/<repo-name>/` on your Android phone and tap **Download Latest APK**.
