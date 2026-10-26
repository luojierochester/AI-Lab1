#pragma once

#include <algorithm>
#include <fstream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>
#include <cstdio>
#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#endif

#ifdef _WIN32
inline std::wstring widePath(const std::string& value) {
    int length = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()), nullptr, 0);
    if (!length) throw std::runtime_error("Invalid UTF-8 path");
    std::wstring result(length, L'\0');
    MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value.data(), static_cast<int>(value.size()), &result[0], length);
    return result;
}
inline std::string utf8Path(const std::wstring& value) {
    int length = WideCharToMultiByte(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), nullptr, 0, nullptr, nullptr);
    std::string result(length, '\0');
    WideCharToMultiByte(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), &result[0], length, nullptr, nullptr);
    return result;
}
#endif

// NPZ1 is a deliberately small, language-independent interchange format.
// Comments begin with #. Every non-comment board line contains n*n integers.
struct PuzzlePath {
    int size = 0;
    std::vector<std::vector<int>> boards;
    std::string source;
    bool demo = false;
    std::size_t steps() const { return boards.empty() ? 0 : boards.size() - 1; }
};

inline std::string contentLine(const std::string& input) {
    auto text = input.substr(0, input.find('#'));
    const auto begin = text.find_first_not_of(" \r\n\t");
    if (begin == std::string::npos) return {};
    return text.substr(begin, text.find_last_not_of(" \r\n\t") - begin + 1);
}

inline std::vector<int> parseNumbers(const std::string& text) {
    std::istringstream stream(text);
    std::vector<int> values;
    int value;
    while (stream >> value) values.push_back(value);
    if (!stream.eof()) throw std::runtime_error("Non-integer value in NPZ1 file");
    return values;
}

inline void validateBoard(const std::vector<int>& board, int n) {
    if (board.size() != static_cast<std::size_t>(n * n))
        throw std::runtime_error("A board must contain exactly size*size values");
    std::vector<bool> seen(n * n, false);
    for (int value : board) {
        if (value < 0 || value >= n * n || seen[value])
            throw std::runtime_error("Board values must be a permutation of 0..size*size-1");
        seen[value] = true;
    }
}

inline int blankPosition(const std::vector<int>& board) {
    return static_cast<int>(std::find(board.begin(), board.end(), 0) - board.begin());
}

inline bool legalTransition(const std::vector<int>& a, const std::vector<int>& b, int n) {
    int oldBlank = blankPosition(a), newBlank = blankPosition(b);
    if (std::abs(oldBlank / n - newBlank / n) + std::abs(oldBlank % n - newBlank % n) != 1)
        return false;
    for (int i = 0; i < n * n; ++i) {
        int expected = i == oldBlank ? a[newBlank] : (i == newBlank ? 0 : a[i]);
        if (b[i] != expected) return false;
    }
    return true;
}

inline PuzzlePath readPath(std::istream& stream, const std::string& source) {
    PuzzlePath path;
    path.source = source;
    std::string line;
    std::vector<std::string> lines;
    while (std::getline(stream, line)) {
        // Accept a UTF-8 BOM written by some Windows editors.
        if (lines.empty() && line.compare(0, 3, "\xEF\xBB\xBF") == 0) line.erase(0, 3);
        line = contentLine(line);
        if (!line.empty()) lines.push_back(line);
        if (lines.size() > 100003) throw std::runtime_error("NPZ1 path exceeds 100000 steps");
    }
    if (lines.size() < 3 || lines[0] != "NPZ1")
        throw std::runtime_error("Missing NPZ1 header or board data");
    auto dimensions = parseNumbers(lines[1]);
    if (dimensions.size() != 2 || dimensions[0] < 2 || dimensions[0] > 4 ||
        dimensions[1] < 0 || dimensions[1] > 100000)
        throw std::runtime_error("Expected size 2..4 and steps 0..100000");
    path.size = dimensions[0];
    if (lines.size() != static_cast<std::size_t>(dimensions[1] + 3))
        throw std::runtime_error("Declared step count does not match the board count");
    for (std::size_t i = 2; i < lines.size(); ++i) {
        auto board = parseNumbers(lines[i]);
        validateBoard(board, path.size);
        if (!path.boards.empty() && !legalTransition(path.boards.back(), board, path.size))
            throw std::runtime_error("Illegal slide at step " + std::to_string(i - 2));
        path.boards.push_back(std::move(board));
    }
    return path;
}

inline PuzzlePath loadPath(const std::string& filename) {
#ifdef _WIN32
    FILE* file = _wfopen(widePath(filename).c_str(), L"rb");
    if (!file) throw std::runtime_error("Cannot open path file: " + filename);
    std::string data;
    char buffer[16384];
    std::size_t count;
    while ((count = std::fread(buffer, 1, sizeof buffer, file))) {
        data.append(buffer, count);
        if (data.size() > 64 * 1024 * 1024) { std::fclose(file); throw std::runtime_error("Path file exceeds 64 MiB"); }
    }
    const bool error = std::ferror(file); std::fclose(file);
    if (error) throw std::runtime_error("Cannot read path file");
    std::istringstream input(data);
#else
    std::ifstream input(filename, std::ios::binary);
    if (!input) throw std::runtime_error("Cannot open path file: " + filename);
#endif
    return readPath(input, filename);
}

inline PuzzlePath demoPath() {
    std::istringstream input(
        "NPZ1\n3 4\n1 2 3 5 0 6 4 7 8\n1 2 3 0 5 6 4 7 8\n"
        "1 2 3 4 5 6 0 7 8\n1 2 3 4 5 6 7 0 8\n1 2 3 4 5 6 7 8 0\n");
    PuzzlePath path = readPath(input, "Built-in 4-step demo");
    path.demo = true;
    return path;
}

// Playback is independent from the renderer and is deterministic under tests.
class Playback {
public:
    explicit Playback(std::size_t last) : last_(last) {}
    std::size_t index() const { return index_; }
    std::size_t target() const { return target_; }
    bool animating() const { return target_ != index_; }
    bool playing() const { return playing_; }
    double fraction() const { return animating() ? std::min(1.0, elapsed_ / duration()) : 0; }
    double speed() const { return speed_; }
    void setSpeed(double value) { speed_ = std::max(0.25, std::min(4.0, value)); }
    void toggle() {
        if (!playing_ && index_ == last_ && !animating()) seek(0);
        playing_ = !playing_;
    }
    void pause() { playing_ = false; }
    void seek(std::size_t value) { index_ = target_ = std::min(last_, value); elapsed_ = 0; playing_ = false; }
    void step(int delta) {
        playing_ = false;
        if (animating()) { index_ = target_; elapsed_ = 0; }
        if (delta > 0 && index_ < last_) target_ = index_ + 1;
        else if (delta < 0 && index_ > 0) target_ = index_ - 1;
    }
    void update(double dt) {
        if (!animating() && playing_ && index_ < last_) { target_ = index_ + 1; elapsed_ = 0; }
        if (animating()) {
            elapsed_ += std::max(0.0, std::min(dt, 0.1));
            if (elapsed_ >= duration()) { index_ = target_; elapsed_ = 0; }
        }
        if (index_ == last_ && !animating()) playing_ = false;
    }
private:
    double duration() const { return 0.55 / speed_; }
    std::size_t index_ = 0, target_ = 0, last_;
    bool playing_ = false;
    double elapsed_ = 0, speed_ = 1;
};
