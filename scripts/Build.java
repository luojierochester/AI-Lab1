import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Build with the JDK alone. Original course sources keep their original encoding. */
class Build {
  public static void main(String[] args) throws Exception {
    Path root = Path.of("").toAbsolutePath();
    if (!Files.isDirectory(root.resolve("src/core")))
      throw new IllegalStateException("Run from the WattAi repository root.");
    Path generated = root.resolve("build/utf8-src"), classes = root.resolve("build/classes");
    Files.createDirectories(classes);
    List<String> arguments =
        new ArrayList<>(List.of("-encoding", "UTF-8", "--release", "17", "-d", classes.toString()));
    try (var files = Files.walk(root.resolve("src"))) {
      for (Path source : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        byte[] bytes = Files.readAllBytes(source);
        String text;
        try {
          text =
              StandardCharsets.UTF_8
                  .newDecoder()
                  .onMalformedInput(CodingErrorAction.REPORT)
                  .decode(ByteBuffer.wrap(bytes))
                  .toString();
        } catch (CharacterCodingException ex) {
          text = Charset.forName("GB18030").newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        Path target = generated.resolve(root.resolve("src").relativize(source));
        Files.createDirectories(target.getParent());
        Files.writeString(target, text, StandardCharsets.UTF_8);
        arguments.add(target.toString());
      }
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) throw new IllegalStateException("A full JDK 17 or later is required.");
    int result = compiler.run(null, System.out, System.err, arguments.toArray(String[]::new));
    if (result != 0) System.exit(result);
    System.out.println("Build OK: " + (arguments.size() - 6) + " Java sources -> build/classes");
  }
}
