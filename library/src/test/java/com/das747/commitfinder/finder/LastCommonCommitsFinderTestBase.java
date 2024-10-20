package com.das747.commitfinder.finder;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.das747.commitfinder.Commit;
import com.das747.commitfinder.Commit.AuthorData;
import com.das747.commitfinder.Commit.CommitData;
import com.das747.commitfinder.Commit.ParentData;
import com.das747.commitfinder.finder.LastCommonCommitsFinderTestBase.TestData.TestCommitData;
import com.das747.commitfinder.api.LastCommonCommitsFinder;
import com.das747.commitfinder.client.GitHubClient;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.testng.TestException;
import org.testng.annotations.Test;

abstract class LastCommonCommitsFinderTestBase {

    private static final String BRANCH_A = "branchA";
    private static final String BRANCH_B = "branchB";

    public static class TestData {

        public Map<String, String> branches;
        public List<TestCommitData> commits;
        public List<String> solution;

        public static class TestCommitData {

            public String sha;
            public List<String> parents;
        }
    }

    protected abstract LastCommonCommitsFinder createFinder(GitHubClient client);

    private TestData loadTestData(String file) throws IOException {
        String path = getClass().getClassLoader().getResource("testData/" + file).getPath();
        var json = new String(Files.readAllBytes(Paths.get(path)));
        var gson = new Gson();
        return gson.fromJson(json, TestData.class);
    }

    private @NotNull Commit createCommit(TestCommitData commitData, Instant timestamp) {
        return new Commit(
            commitData.sha,
            commitData.parents.stream().map(ParentData::new).toList(),
            new CommitData(new AuthorData(timestamp))
        );
    }

    private @NotNull GitHubClient prepareMockClient(@NotNull TestData testData) throws IOException {
        var mockedClient = mock(GitHubClient.class);
        var timestamp = Instant.now();
        for (var commitData : testData.commits) {
            when(mockedClient.getCommit(commitData.sha)).thenReturn(
                createCommit(commitData, timestamp));
            timestamp = timestamp.plus(Duration.ofMinutes(1));
        }
        for (var entry : testData.branches.entrySet()) {
            when(mockedClient.getHeadCommitSha(entry.getKey())).thenReturn(entry.getValue());
        }

        return mockedClient;
    }

    private void doTest(String name) {
        try {
            var testData = loadTestData(name + ".json");
            var finder = createFinder(prepareMockClient(testData));
            var result = finder.findLastCommonCommits(BRANCH_A, BRANCH_B);
            var resultRepetition = finder.findLastCommonCommits(BRANCH_A, BRANCH_B);
            var resultSwap = finder.findLastCommonCommits(BRANCH_B, BRANCH_A);
            assertEqualsNoOrder(result, testData.solution);
            assertEqualsNoOrder(resultRepetition, testData.solution);
            assertEqualsNoOrder(resultSwap, testData.solution);
        } catch (IOException e) {
            throw new TestException(e);
        }
    }

    /*
            1 <- branchA
          /
         0----2 <- branchB
     */
    @Test
    public void graphWithSimpleBranching() {
        doTest("simpleBranching");
    }

    /*
                 <- branchA
        0---1---2---3 <- branchB
     */
    @Test
    public void graphWithoutBranching() {
        doTest("withoutBranching");
    }

    /*
        0---1---2---3 <- branchA, branchB
     */
    @Test
    public void graphWithEqualBranches() {
        doTest("equalBranches");
    }

    /*
             2---4 <- branchA
           /  \ /
          /   / \
         0---1---3 <- branchB
     */
    @Test
    public void graphWithCrossMerge() {
        doTest("crossMerge");
    }

    /*
          +---2 <- branchA
         /   /
        0---1
         \   \
          +---3 <- branchB
     */
    @Test
    public void graphWithRelatedCommonCommits() {
        doTest("relatedCommonCommits");
    }

    /*
            1 <- branchA
           /   3 <- branchB
          /   /
         0---2---4 <- branchС
     */
    @Test
    public void graphWithThreeBranches() {
        try {
            var testData = loadTestData("threeBranches.json");
            var finder = createFinder(prepareMockClient(testData));
            var firstResult = finder.findLastCommonCommits("branchA", "branchB");
            var secondResult = finder.findLastCommonCommits("branchB", "branchC");
            assertEqualsNoOrder(firstResult, List.of("0"));
            assertEqualsNoOrder(secondResult, List.of("2"));
        } catch (IOException e) {
            throw new TestException(e);
        }
    }

}