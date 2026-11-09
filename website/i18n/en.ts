import type { Messages } from "./types.ts";

export const en: Messages = {
  name: "English",
  htmlLang: "en",
  ogLocale: "en_US",
  dateLocale: "en-US",
  site: {
    name: "Papy",
    brandSub: "",
    skip: "Skip to content",
    home: "Papy home",
    mainNav: "Main menu",
    language: "Language",
    licenseNote: "Free for noncommercial use · commercial license available",
    legal:
      "This product was developed with reference to Hancom's published HWP document file (.hwp) format documents. “Hangul”, “Hancom”, “HWP” and “HWPX” are registered trademarks of Hancom Inc.; Microsoft Word is a trademark of Microsoft Corporation. Papy is an independent product with no affiliation, sponsorship or endorsement by these companies.",
    github: "GitHub",
    editPage: "Suggest an edit to this page",
    privacy: "Privacy policy",
  },
  pages: {
    home: {
      title: "Papy — Free, offline PDF, HWP and DOCX document editor",
      description:
        "Papy is a source-available app for viewing and editing PDF, Hangul (HWP/HWPX) and Word (DOCX, DOC) documents, free for personal and noncommercial use. Switch between view and edit mode, save and convert formats on your own computer, with no sign-up, ads or uploads. Windows, macOS and Linux.",
      nav: "About",
    },
    download: {
      title: "Download — Papy",
      description: "Download Papy for Windows, macOS and Linux for free. Every installer is built in public and published on GitHub Releases.",
      nav: "Download",
    },
    install: {
      title: "Installation — Papy",
      description: "How to install Papy on Windows (.msi/.exe), macOS (.dmg) and Linux (.AppImage/.deb), and how to open it when a security warning appears.",
      nav: "Install",
    },
    usage: {
      title: "User guide — Papy",
      description: "How to open documents in Papy, edit them in edit mode, save, save as and convert formats, and what is supported.",
      nav: "Guide",
    },
    developers: {
      title: "Developers — Contribute to Papy",
      description: "How to contribute to Papy: the GitHub repository, filing issues, the fork, branch and pull request workflow, development setup and repository layout.",
      nav: "Developers",
    },
    contact: {
      title: "Contact — Papy",
      description: "Send bug reports, feature ideas, usage questions and commercial-use inquiries straight to the Papy developer.",
      nav: "Contact",
    },
    privacy: {
      title: "Privacy policy — Papy",
      description: "Papy works without accounts, ads or tracking and never sends your documents to a server. This page explains what the app and this website process.",
    },
    notFound: {
      title: "Page not found — Papy",
      description: "The page you requested does not exist.",
    },
  },
  notFound: {
    heading: "Page not found",
    body: "The address may have changed, or the page does not exist.",
    home: "Go home",
    download: "Download",
  },
  client: {
    contact: {
      sent: "Your message has been sent. We will reply to the email address you entered.",
      failed: "Your message could not be sent. Please try again later or use GitHub Issues.",
      errors: {
        forbidden: "This request is not allowed.",
        badRequest: "The request format is invalid.",
        type: "Please choose a topic.",
        email: "Please check the email address for our reply.",
        message: "Please write at least {min} characters.",
      },
    },
    download: {
      latest: "Latest version: {name}{date}",
      prerelease: " (preview build — cannot open documents yet)",
      none: "No installers have been published yet. They will appear here automatically after the first build. {link}",
      failed: "Could not load version information. Please download directly from {link}.",
      viewOnGithub: "View on GitHub Releases",
      labels: {
        exe: "Installer (.exe, recommended)",
        msi: "MSI package",
        dmgArm: "Apple Silicon (.dmg)",
        dmgIntel: "Intel (.dmg)",
        dmgUniversal: "Universal (.dmg)",
        appImage: "AppImage",
        deb: "Debian/Ubuntu (.deb)",
        rpm: "Fedora/openSUSE (.rpm)",
      },
    },
    developers: {
      empty: "Nothing right now.",
    },
  },
};
