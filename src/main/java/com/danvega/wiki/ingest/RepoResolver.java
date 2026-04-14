package com.danvega.wiki.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code repo} front-matter fields that contain GitHub URLs by
 * shallow-cloning the repository into a local {@code repos/} directory.
 * Raw files are <em>not</em> modified — the original GitHub URL is preserved.
 * After compilation the cloned repos can be cleaned up via {@link #cleanup()}.
 */
@Component
public class RepoResolver {

    private static final Logger log = LoggerFactory.getLogger(RepoResolver.class);
    private static final Path REPOS_DIR = Path.of("repos");
    private static final Pattern REPO_FIELD = Pattern.compile("^repo:\\s*(.+)$", Pattern.MULTILINE);
    private static final String GITHUB_PREFIX = "https://github.com/";

    /**
     * Scan the given raw files for {@code repo} front-matter fields pointing to
     * GitHub URLs. Clone each unique repo once (shallow, depth 1) into
     * {@code repos/<name>}. Returns a map of GitHub URL → local clone path
     * so callers can inform the compiler where the code lives.
     */
    public Map<String, Path> resolveRepos(List<Path> rawFiles) throws Exception {
        Map<String, Path> resolved = new HashMap<>();

        for (Path file : rawFiles) {
            String content = Files.readString(file);
            Matcher m = REPO_FIELD.matcher(content);
            if (!m.find()) continue;

            String repoValue = m.group(1).trim();
            if (!repoValue.startsWith(GITHUB_PREFIX)) continue;
            if (resolved.containsKey(repoValue)) continue;

            String repoName = extractRepoName(repoValue);
            Path localPath = REPOS_DIR.resolve(repoName);

            if (!Files.exists(localPath)) {
                cloneRepo(repoValue, localPath);
            }
            resolved.put(repoValue, localPath);
            log.info("Resolved repo {} → {}", repoValue, localPath);
        }
        return resolved;
    }

    /** Delete all cloned repos. */
    public void cleanup() {
        if (!Files.exists(REPOS_DIR)) return;
        try (var walk = Files.walk(REPOS_DIR)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException e) {
                            log.warn("Failed to delete {}: {}", p, e.getMessage());
                        }
                    });
            log.info("Cleaned up repos/ directory");
        } catch (IOException e) {
            log.warn("Failed to walk repos/ for cleanup: {}", e.getMessage());
        }
    }

    private void cloneRepo(String url, Path target) throws Exception {
        log.info("Shallow-cloning {} → {}", url, target);
        Files.createDirectories(target.getParent());
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", url, target.toString());
        pb.inheritIO();
        int exit = pb.start().waitFor();
        if (exit != 0) {
            throw new RuntimeException("git clone failed with exit code " + exit + " for " + url);
        }
    }

    private static String extractRepoName(String url) {
        String path = url.substring(GITHUB_PREFIX.length());
        if (path.endsWith(".git")) path = path.substring(0, path.length() - 4);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
