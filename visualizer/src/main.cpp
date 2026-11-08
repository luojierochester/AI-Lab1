#include "path.hpp"
#include <SFML/Graphics.hpp>
#include <cmath>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <sstream>

#ifdef _WIN32
#include <commdlg.h>
#include <shellapi.h>
#include <imm.h>
#endif

namespace {
const sf::Color bg(15, 20, 31), panel(24, 32, 47), tile(221, 229, 237);
const sf::Color ink(233, 239, 248), muted(153, 169, 193), accent(110, 224, 196);
const sf::Color gold(246, 198, 106), dim(38, 48, 66);
constexpr float width = 1120, height = 800;

sf::String utf8(const std::string& s) { return sf::String::fromUtf8(s.begin(), s.end()); }
std::string number(double value) { std::ostringstream s; s << std::fixed << std::setprecision(2) << value; return s.str(); }

void box(sf::RenderTarget& out, float x, float y, float w, float h, sf::Color color, float border = 0, sf::Color outline = dim) {
    sf::RectangleShape r({w, h}); r.setPosition(x, y); r.setFillColor(color);
    r.setOutlineThickness(border); r.setOutlineColor(outline); out.draw(r);
}

void text(sf::RenderTarget& out, const sf::Font& font, const std::string& value,
          float x, float y, unsigned size = 20, sf::Color color = ink) {
    sf::Text t(utf8(value), font, size); t.setPosition(x, y); t.setFillColor(color); out.draw(t);
}

void center(sf::RenderTarget& out, const sf::Font& font, const std::string& value,
            float x, float y, unsigned size, sf::Color color) {
    sf::Text t(utf8(value), font, size); auto b = t.getLocalBounds();
    t.setOrigin(b.left + b.width / 2, b.top + b.height / 2); t.setPosition(x, y); t.setFillColor(color); out.draw(t);
}

std::string basename(const std::string& path) {
    auto pos = path.find_last_of("/\\");
    return pos == std::string::npos ? path : path.substr(pos + 1);
}

bool loadFont(sf::Font& font, const std::string& explicitPath) {
    std::vector<std::string> paths;
    if (!explicitPath.empty()) paths.push_back(explicitPath);
    if (const char* env = std::getenv("NPZ_FONT")) paths.emplace_back(env);
    paths.insert(paths.end(), {"assets/NotoSansSC-Regular.ttf", "visualizer/assets/NotoSansSC-Regular.ttf",
        "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/System/Library/Fonts/PingFang.ttc"});
    for (const auto& path : paths) { std::ifstream f(path, std::ios::binary); if (f && font.loadFromFile(path)) return true; }
    return false;
}

struct Button { sf::FloatRect rect; std::string label; int command; bool primary = false; };

void button(sf::RenderTarget& out, const sf::Font& font, const Button& b, sf::Vector2f mouse, bool enabled = true) {
    auto color = b.primary ? accent : dim;
    if (!enabled) color = sf::Color(32, 40, 54);
    else if (b.rect.contains(mouse)) color = b.primary ? sf::Color(148, 242, 217) : sf::Color(57, 71, 94);
    box(out, b.rect.left, b.rect.top, b.rect.width, b.rect.height, color);
    center(out, font, b.label, b.rect.left + b.rect.width / 2, b.rect.top + b.rect.height / 2,
           18, !enabled ? muted : b.primary ? bg : ink);
}

void board(sf::RenderTarget& out, const sf::Font& font, const PuzzlePath& path, const Playback& playback) {
    constexpr float left = 40, top = 162, side = 510, gap = 8;
    const int n = path.size;
    const float cell = side / n;
    box(out, left - 10, top - 10, side + 20, side + 20, panel);
    const auto& a = path.boards[playback.index()];
    const auto& b = path.boards[playback.target()];
    float progress = static_cast<float>(playback.fraction());
    progress = progress * progress * (3 - 2 * progress);
    for (int pos = 0; pos < n * n; ++pos) {
        float x = left + (pos % n) * cell, y = top + (pos / n) * cell;
        box(out, x + gap / 2, y + gap / 2, cell - gap, cell - gap, bg);
    }
    for (int pos = 0; pos < n * n; ++pos) {
        if (!a[pos]) continue;
        const int targetPos = static_cast<int>(std::find(b.begin(), b.end(), a[pos]) - b.begin());
        const bool moving = pos != targetPos;
        float x = left + (pos % n + (targetPos % n - pos % n) * progress) * cell;
        float y = top + (pos / n + (targetPos / n - pos / n) * progress) * cell;
        sf::Color color = moving ? accent : tile;
        if (playback.index() == path.steps() && !playback.animating()) color = accent;
        box(out, x + gap / 2, y + gap / 2 + 4, cell - gap, cell - gap, sf::Color(5, 9, 15));
        box(out, x + gap / 2, y + gap / 2, cell - gap, cell - gap, color);
        center(out, font, std::to_string(a[pos]), x + cell / 2, y + cell / 2, n == 4 ? 44 : 54, bg);
    }
}

std::string direction(int from, int to, int n) {
    if (to - from == 1) return "向右";
    if (to - from == -1) return "向左";
    if (to - from == n) return "向下";
    return "向上";
}

void miniBoard(sf::RenderTarget& out, const sf::Font& font, const PuzzlePath& path, float x, float y) {
    const float cell = 30;
    const auto& state = path.boards.back();
    for (int i = 0; i < path.size * path.size; ++i) {
        box(out, x + i % path.size * cell, y + i / path.size * cell, cell - 3, cell - 3, state[i] ? dim : bg);
        if (state[i]) center(out, font, std::to_string(state[i]), x + i % path.size * cell + 13,
            y + i / path.size * cell + 13, 14, muted);
    }
}

void render(sf::RenderTarget& out, const sf::Font& font, const PuzzlePath& path, const Playback& playback,
            const std::vector<Button>& buttons, sf::Vector2f mouse, const std::string& notice) {
    out.clear(bg);
    box(out, 40, 36, 5, 49, accent);
    text(out, font, "N-PUZZLE", 60, 27, 31);
    if (path.demo) text(out, font, "演示数据 · 内置四步示例，不计入实验结果", 41, 99, 18, gold);
    button(out, font, {{938, 38, 142, 43}, "打开路径文件", 7}, mouse);

    board(out, font, path, playback);
    box(out, 586, 152, 494, 178, panel);
    text(out, font, "路径进度", 608, 168, 18, muted);
    text(out, font, std::to_string(playback.index()), 605, 193, 58, accent);
    text(out, font, "/ " + std::to_string(path.steps()) + " 步", 742, 224, 23, muted);
    text(out, font, std::to_string(path.size) + " × " + std::to_string(path.size) + " 棋盘", 608, 288, 18);
    text(out, font, playback.playing() ? "● 自动播放" : playback.index() == path.steps() ? "● 路径播放完成" : "● 已暂停", 865, 288, 18, accent);

    box(out, 586, 346, 494, 165, panel);
    text(out, font, "下一次滑动", 608, 361, 18, muted);
    if (playback.index() < path.steps()) {
        const auto& current = path.boards[playback.index()];
        const auto& next = path.boards[playback.index() + 1];
        int from = blankPosition(next), to = blankPosition(current);
        text(out, font, "将牌 " + std::to_string(current[from]) + "  " + direction(from, to, path.size), 607, 395, 32);
        text(out, font, "空格反向移动一格，步代价 = 1", 609, 465, 17, muted);
    } else {
        text(out, font, "已到达路径终点", 607, 395, 30, accent);
        text(out, font, "空格键重新播放，或打开另一条路径", 609, 465, 17, muted);
    }

    text(out, font, "目标状态（路径末帧）", 609, 533, 17, muted);
    miniBoard(out, font, path, 920, 531);
    text(out, font, "速度  " + number(playback.speed()) + "×", 609, 568, 22);
    text(out, font, "文件  " + basename(path.source).substr(0, 36), 609, 608, 15, muted);

    box(out, 40, 695, 1040, 2, dim);
    for (const auto& b : buttons) button(out, font, b, mouse);
    text(out, font, "空格 播放/暂停    ← → 单步    Home 复位    ↑ ↓ 速度    O 打开    Esc 退出", 42, 769, 15, muted);
    const float fraction = path.steps() ? static_cast<float>(playback.index()) / path.steps() : 1;
    box(out, 609, 661, 447, 5, dim); box(out, 609, 661, 447 * fraction, 5, accent);
    sf::CircleShape dot(6); dot.setPosition(603 + 447 * fraction, 657); dot.setFillColor(accent); out.draw(dot);
    if (!notice.empty()) { box(out, 40, 116, 1040, 28, panel); text(out, font, notice, 47, 117, 16, gold); }
}

std::string openDialog(sf::WindowHandle handle) {
#ifdef _WIN32
    wchar_t file[32768] = {};
    OPENFILENAMEW dialog{}; dialog.lStructSize = sizeof(dialog); dialog.hwndOwner = handle;
    dialog.lpstrFilter = L"N-Puzzle path (*.npz;*.txt)\0*.npz;*.txt\0All files\0*.*\0";
    dialog.lpstrFile = file; dialog.nMaxFile = 32768; dialog.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_NOCHANGEDIR;
    if (GetOpenFileNameW(&dialog)) return utf8Path(file);
#else
    (void)handle;
#endif
    return {};
}

int selfTest() {
    int tests = 0;
    auto check = [&](bool condition, const char* label) { if (!condition) throw std::runtime_error(label); ++tests; };
    auto reject = [&](const std::string& data) { try { std::istringstream s(data); readPath(s, "test"); return false; } catch (...) { return true; } };
    auto p = demoPath(); check(p.steps() == 4, "demo length");
    check(reject("BAD\n3 0\n1 2 3 4 5 6 7 8 0"), "bad header");
    check(reject("NPZ1\n3 1\n1 2 3 4 5 6 7 8 0"), "count mismatch");
    check(reject("NPZ1\n3 0\n1 2 3 4 5 6 7 7 0"), "duplicate tile");
    check(reject("NPZ1\n3 1\n1 2 3 4 5 6 0 7 8\n1 2 3 4 5 6 7 8 0"), "teleport");
    check(reject("NPZ1\n3 0\n1 2 3 4 5 6 7 8 x"), "bad integer");
    check(reject("NPZ1\n3 -1\n1 2 3 4 5 6 7 8 0"), "negative steps");
    check(reject("NPZ1\n3 0\n1 2 3 4 5 6 7 8 9"), "out of range");
    std::istringstream comments("\xEF\xBB\xBFNPZ1 #header\n# note\n2 0\n1 2 3 0 #goal\n");
    check(readPath(comments, "comments").steps() == 0, "comments/BOM/zero path");
    Playback player(4); player.toggle(); for (int i = 0; i < 100; ++i) player.update(0.1);
    check(player.index() == 4 && !player.playing(), "autoplay end");
    player.toggle(); check(player.index() == 0 && player.playing(), "replay");
    player.pause(); player.step(1); for (int i = 0; i < 10; ++i) player.update(0.1);
    check(player.index() == 1 && !player.playing(), "manual next");
    player.step(-1); for (int i = 0; i < 10; ++i) player.update(0.1);
    check(player.index() == 0, "manual previous");
    player.seek(999); check(player.index() == 4, "seek clamp");
    player.setSpeed(100); check(player.speed() == 4, "speed upper clamp");
    player.setSpeed(-1); check(player.speed() == 0.25, "speed lower clamp");
    Playback empty(0); empty.toggle(); empty.update(.1); check(!empty.playing(), "zero step playback");
    std::cout << "PASS: " << tests << " viewer parser/playback checks\n"; return 0;
}
} // namespace

int main(int argc, char** argv) {
    try {
        std::vector<std::string> arguments;
#ifdef _WIN32
        int wideCount = 0;
        LPWSTR* wideArguments = CommandLineToArgvW(GetCommandLineW(), &wideCount);
        if (!wideArguments) throw std::runtime_error("Cannot read command line");
        for (int i = 0; i < wideCount; ++i) arguments.push_back(utf8Path(wideArguments[i]));
        LocalFree(wideArguments);
        (void)argc; (void)argv;
#else
        for (int i = 0; i < argc; ++i) arguments.emplace_back(argv[i]);
#endif
        std::string input, capture, fontPath;
        int frame = 0;
        bool validate = false, autoplay = false;
        for (std::size_t i = 1; i < arguments.size(); ++i) {
            std::string arg = arguments[i];
            if (arg == "--self-test") return selfTest();
            if (arg == "--validate") validate = true;
            else if (arg == "--play") autoplay = true;
            else if (arg == "--capture" && i + 1 < arguments.size()) capture = arguments[++i];
            else if (arg == "--frame" && i + 1 < arguments.size()) frame = std::stoi(arguments[++i]);
            else if (arg == "--font" && i + 1 < arguments.size()) fontPath = arguments[++i];
            else if (arg == "--help") { std::cout << "npuzzle-viewer [path.npz] [--play] [--validate] [--capture out.png --frame N] [--font font.ttf]\n"; return 0; }
            else if (arg.size() && arg[0] == '-') throw std::runtime_error("Unknown or incomplete option: " + arg);
            else if (input.empty()) input = arg;
            else throw std::runtime_error("Only one path file may be specified");
        }
        PuzzlePath path = input.empty() ? demoPath() : loadPath(input);
        if (validate) { std::cout << "VALID: " << path.size << "x" << path.size << ", " << path.steps() << " legal moves\n"; return 0; }
        sf::Font font;
        if (!loadFont(font, fontPath)) throw std::runtime_error("No Chinese font found. Use --font font.ttf or set NPZ_FONT");
        sf::RenderWindow window(sf::VideoMode(static_cast<unsigned>(width), static_cast<unsigned>(height)), utf8("N-Puzzle | SFML"));
#ifdef _WIN32
        // This canvas has no text fields. Keep the user's Chinese IME from
        // swallowing letter shortcuts; native file dialogs retain their IME.
        ImmAssociateContext(window.getSystemHandle(), nullptr);
#endif
        window.setFramerateLimit(60); window.setKeyRepeatEnabled(false);
        Playback playback(path.steps()); playback.seek(static_cast<std::size_t>(std::max(0, frame)));
        if (autoplay) playback.toggle();
        sf::Clock clock;
        std::string notice;
        auto open = [&]() {
            playback.pause();
            const auto file = openDialog(window.getSystemHandle());
            if (!file.empty()) {
                try { path = loadPath(file); playback = Playback(path.steps()); notice.clear(); }
                catch (const std::exception& ex) { notice = "文件未通过校验，已保留当前路径。详情见控制台。"; std::cerr << ex.what() << '\n'; }
            }
        };
        while (window.isOpen()) {
            std::vector<Button> buttons = {
                {{40, 716, 140, 42}, playback.playing() ? "暂停" : "播放", 0, true},
                {{194, 716, 115, 42}, "上一步", 1}, {{323, 716, 115, 42}, "下一步", 2},
                {{452, 716, 98, 42}, "复位", 3}, {{609, 716, 90, 42}, "减速 −", 4},
                {{713, 716, 90, 42}, "加速 +", 5}, {{922, 716, 158, 42}, "跳到终点", 6}
            };
            auto command = [&](int id) {
                switch (id) { case 0: playback.toggle(); break; case 1: playback.step(-1); break;
                    case 2: playback.step(1); break; case 3: playback.seek(0); break;
                    case 4: playback.setSpeed(playback.speed() / 2); break;
                    case 5: playback.setSpeed(playback.speed() * 2); break;
                    case 6: playback.seek(path.steps()); break; case 7: open(); break; }
            };
            sf::Event event;
            while (window.pollEvent(event)) {
                if (event.type == sf::Event::Closed) window.close();
                if (event.type == sf::Event::Resized && event.size.width && event.size.height) {
                    sf::View view(sf::FloatRect(0, 0, width, height));
                    const float ratio = static_cast<float>(event.size.width) / event.size.height;
                    if (ratio > width / height) { float scale = width / height / ratio; view.setViewport({(1 - scale) / 2, 0, scale, 1}); }
                    else { float scale = ratio / (width / height); view.setViewport({0, (1 - scale) / 2, 1, scale}); }
                    window.setView(view);
                }
                if (event.type == sf::Event::KeyPressed) {
                    switch (event.key.code) {
                        case sf::Keyboard::Escape: window.close(); break;
                        case sf::Keyboard::Space: command(0); break;
                        case sf::Keyboard::Left: command(1); break;
                        case sf::Keyboard::Right: command(2); break;
                        case sf::Keyboard::Home: command(3); break;
                        case sf::Keyboard::Down: command(4); break;
                        case sf::Keyboard::Up: command(5); break;
                        case sf::Keyboard::End: command(6); break;
                        case sf::Keyboard::O: command(7); break; default: break;
                    }
                }
                if (event.type == sf::Event::MouseButtonPressed && event.mouseButton.button == sf::Mouse::Left) {
                    auto mouse = window.mapPixelToCoords({event.mouseButton.x, event.mouseButton.y});
                    for (const auto& b : buttons) if (b.rect.contains(mouse)) command(b.command);
                    if (sf::FloatRect(938, 38, 142, 43).contains(mouse)) command(7);
                    if (sf::FloatRect(603, 648, 459, 30).contains(mouse))
                        playback.seek(static_cast<std::size_t>(std::round(std::max(0.f, std::min(1.f, (mouse.x - 609) / 447)) * path.steps())));
                }
            }
            if (!window.isOpen()) break;
            playback.update(clock.restart().asSeconds());
            render(window, font, path, playback, buttons, window.mapPixelToCoords(sf::Mouse::getPosition(window)), notice);
            if (!capture.empty()) {
                sf::Texture texture; if (!texture.create(window.getSize().x, window.getSize().y)) throw std::runtime_error("Capture texture allocation failed");
                texture.update(window);
                if (!texture.copyToImage().saveToFile(capture)) throw std::runtime_error("Cannot save capture: " + capture);
                std::cout << "CAPTURED: " << capture << '\n'; window.close();
            } else window.display();
        }
        return 0;
    } catch (const std::exception& ex) { std::cerr << "ERROR: " << ex.what() << '\n'; return 1; }
}
