**TTS-TOOL: Text-to-Speech Utility**

This README provides an overview of the TTS-TOOL application, detailing its features, usage, and how it handles text input, along with important notes regarding its deployment.

**Utility**

TTS-TOOL is a simple yet effective text-to-speech (TTS) application designed to load written text, organize the texts, and record into unprocessed raw audio (.wav). It provides a convenient way to listen to text content, which can be beneficial for various purposes such as reviewing written material, aiding in accessibility, or simply consuming content hands-free.

**Features**

Text File Loading: Allows users to load text directly from a file, making it easy to process larger documents.

Intuitive Text Display: Provides a clear and user-friendly interface for displaying loaded texts.

Audio Recording for TTS Training: Enables recording of raw audio in WAV format, specifically designed for use in Text-to-Speech model training.

Audio Management: Offers functionality to play and delete recorded audio files.

Session Management: Features to save and continue sessions, ensuring your progress and data are preserved across uses. This leverages Google Firestore when deployed from the GitHub repository.

User-Friendly Interface: A straightforward interface for easy text input and playback control.

Clear Text Formatting Requirements: Specific input formatting ensures optimal speech synthesis.

**Usage**

Input Text: Load the text corpus you wish to record into audio in the provided text area.

Format Sentences: It is crucial to format your input text such that each sentence is separated by an empty newline. This ensures that even if a sentence spill into multiple lines, a empty line in between sentences serves as the boundary.

**Example of Correct Formatting:**

_This is the first sentence._

_This is the second sentence, and it starts on a new line after an empty line._

_And here is a third sentence._



**Generate Speech:** Click the "Start Recording" similar button to record your formatted text into audio.

**Playback & Recording:** Use the playback controls (e.g., play, pause) to listen to the generated audio.

**Manage Audio & Sessions:** Utilize options to delete unwanted audio files and to save or continue your current session.


### 🎬 Demo Video

[![Watch the demo](https://raw.githubusercontent.com/Emperor-Trillion/TTS-Tool/refs/heads/master/Screenshot_20250729-081241.jpg)](https://youtube.com/shorts/7m6aYRZU8fY?feature=share)

> Click the image above to watch the demo on YouTube.


**Creation and Deployment**
The TTS-TOOL application is mainly built with Android Studio and Java, integrating with Android's built-in record capabilities, and file exploration for managing and organizing files in a workspace.

**Important Note for GitHub Repository Users:**

If you are deploying or running the TTS-TOOL application directly from the provided GitHub repository (https://github.com/Emperor-Trillion/TTS-Tool), please be aware that it requires Google Firestore to save user sessions. You will need to set up a Firestore database and configure your application with the appropriate Firebase credentials for the session-saving feature to function correctly. Without Firestore configured, the application will still perform text-to-speech conversion, but session data will not be persisted.

**You may also send me an email if you need the app, which is configured Firestore to save and load session.**
