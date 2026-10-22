package io.github.quanghung304.jobhunter.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.common.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileServiceTest {

    @TempDir
    Path dir;

    Path file;
    byte[] original;
    final List<Object> events = new ArrayList<>();
    ProfileService service;

    @BeforeEach
    void setUp() throws IOException {
        file = dir.resolve("profile.yaml");
        Files.copy(ProfileFixtures.EXAMPLE, file);
        original = Files.readAllBytes(file);
        service = ProfileFixtures.service(file, event -> {
            events.add(event);
            if (event instanceof ProfileChanged changed) {
                changed.reassessed(new ProfileChanged.Reassessment(30, 2, 13));
            }
        });
    }

    private ObjectNode treeOfFile() {
        try {
            ObjectNode tree = (ObjectNode) ProfileParser.YAML.readTree(Files.readAllBytes(file));
            ArrayNode tracks = (ArrayNode) tree.get("tracks");
            tracks.forEach(t -> ((ObjectNode) t).retain("name", "enabled"));
            return tree;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Profile editedYaml(java.util.function.Consumer<ObjectNode> change) {
        ObjectNode tree = treeOfFile();
        change.accept(tree);
        return service.parse(tree);
    }

    @Test
    void savingAValidProfileRewritesTheFileKeepsABackupAndPublishesTheChange() throws IOException {
        String baseHash = service.current().hash();
        Profile profile = editedYaml(tree -> ((ObjectNode) tree.get("preferences")).put("maxHoursPerWeek", 30));

        ProfileService.SaveResult result = service.save(profile, baseHash);

        assertThat(Files.readAllBytes(dir.resolve("profile.yaml.bak"))).isEqualTo(original);
        byte[] written = Files.readAllBytes(file);
        assertThat(new String(written, StandardCharsets.UTF_8)).contains("maxHoursPerWeek: 30", "subjects:");
        assertThat(result.snapshot().hash()).isEqualTo(Hashing.sha256Hex(written)).isNotEqualTo(baseHash);
        assertThat(service.current().profile().preferences().maxHoursPerWeek()).isEqualTo(30);
        assertThat(result.reassessed()).isEqualTo(new ProfileChanged.Reassessment(30, 2, 13));
        assertThat(events).singleElement().isInstanceOfSatisfying(ProfileChanged.class, changed -> {
            assertThat(changed.oldHash()).isEqualTo(baseHash);
            assertThat(changed.newHash()).isEqualTo(result.snapshot().hash());
            assertThat(changed.profile().preferences().maxHoursPerWeek()).isEqualTo(30);
        });
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .as("no temp file left").containsExactlyInAnyOrder("profile.yaml", "profile.yaml.bak");
        }
    }

    @Test
    void theSavedFileLoadsAtStartupAndASecondSaveUsesTheNewHash() {
        ProfileService.SaveResult first = service.save(editedYaml(tree -> { }), service.current().hash());

        assertThat(ProfileFixtures.load(file).profileHash()).isEqualTo(first.snapshot().hash());
        ProfileService.SaveResult second = service.save(
                editedYaml(tree -> ((ObjectNode) tree.get("notify")).put("digestMinScore", 70)),
                first.snapshot().hash());
        assertThat(second.snapshot().profile().notifySettings().digestMinScore()).isEqualTo(70);
    }

    @Test
    void anInvalidProfileListsEveryProblemByPathAndLeavesTheFile() throws IOException {
        Profile profile = editedYaml(tree -> {
            ObjectNode candidate = (ObjectNode) tree.get("candidate");
            candidate.put("headline", " ");
            candidate.put("residenceCountry", "Italy");
            ((ObjectNode) tree.get("preferences")).putArray("acceptRegions").add("EU").add("Mars");
            ((ObjectNode) tree.get("notify")).put("digestMinScore", 101);
        });

        assertThatThrownBy(() -> service.save(profile, service.current().hash()))
                .isInstanceOfSatisfying(ProfileValidationException.class, e -> assertThat(e.problems())
                        .extracting(ProfileProblem::path)
                        .containsExactly("candidate.headline", "candidate.residenceCountry", "notify.digestMinScore",
                                "preferences.acceptRegions[1]"));
        assertUnchanged();
    }

    @Test
    void aMappingErrorNamesItsPath() {
        ObjectNode tree = treeOfFile();
        ((ArrayNode) tree.get("prefilter").get("titleInclude")).set(0, "([unclosed");

        assertThatThrownBy(() -> service.parse(tree))
                .isInstanceOfSatisfying(ProfileValidationException.class, e -> assertThat(e.problems())
                        .singleElement()
                        .satisfies(p -> {
                            assertThat(p.path()).isEqualTo("prefilter.titleInclude[0]");
                            assertThat(p.message()).contains("invalid regex");
                        }));
    }

    @Test
    void unknownKeysAreRejectedAsAtStartup() {
        ObjectNode tree = treeOfFile();
        ((ObjectNode) tree.get("candidate")).put("nickname", "H");

        assertThatThrownBy(() -> service.parse(tree))
                .isInstanceOfSatisfying(ProfileValidationException.class,
                        e -> assertThat(e.problems().get(0).message()).contains("nickname"));
    }

    @Test
    void theIdCannotChange() throws IOException {
        Profile profile = editedYaml(tree -> tree.put("id", "someone-else"));

        assertThatThrownBy(() -> service.save(profile, service.current().hash()))
                .isInstanceOf(ProfileIdImmutableException.class);
        assertUnchanged();
    }

    @Test
    void aStaleHashIsRefused() throws IOException {
        Profile profile = editedYaml(tree -> { });

        assertThatThrownBy(() -> service.save(profile, "not-the-hash"))
                .isInstanceOfSatisfying(ProfileChangedException.class,
                        e -> assertThat(e.httpStatus()).isEqualTo(409));
        assertUnchanged();
    }

    @Test
    void aFileEditedByHandSinceLoadingIsNotOverwritten() throws IOException {
        String baseHash = service.current().hash();
        byte[] handEdited = (new String(original, StandardCharsets.UTF_8) + "\n# edited by hand\n")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(file, handEdited);

        assertThatThrownBy(() -> service.save(editedYaml(tree -> { }), baseHash))
                .isInstanceOf(ProfileChangedException.class)
                .hasMessageContaining("restart");
        assertThat(Files.readAllBytes(file)).isEqualTo(handEdited);
        assertThat(events).isEmpty();
    }

    @Test
    void replaceWritesThroughATempFileAndKeepsTheOldOneAsBak() throws IOException {
        Path target = dir.resolve("other.yaml");
        Files.writeString(target, "old");

        ProfileService.replace(target, "new".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(target)).isEqualTo("new");
        assertThat(Files.readString(dir.resolve("other.yaml.bak"))).isEqualTo("old");
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp"));
        }
    }

    private void assertUnchanged() throws IOException {
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
        assertThat(dir.resolve("profile.yaml.bak")).doesNotExist();
        assertThat(service.current().hash()).isEqualTo(Hashing.sha256Hex(original));
        assertThat(events).isEmpty();
    }
}
