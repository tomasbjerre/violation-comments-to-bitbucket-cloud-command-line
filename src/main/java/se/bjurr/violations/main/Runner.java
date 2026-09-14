package se.bjurr.violations.main;

import static se.bjurr.violations.lib.ViolationsApi.violationsApi;
import static se.bjurr.violations.lib.model.SEVERITY.INFO;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import se.bjurr.violations.comments.bitbucketcloud.lib.ViolationCommentsToBitbucketCloudApi;
import se.bjurr.violations.lib.FilteringViolationsLogger;
import se.bjurr.violations.lib.ViolationsLogger;
import se.bjurr.violations.lib.model.SEVERITY;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.reports.Parser;
import se.bjurr.violations.lib.util.Filtering;

@Command(name = "violation-comments-to-bitbucket-cloud-command-line")
public class Runner {

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Show this help message and exit.")
  private boolean help;

  @Option(
      names = {"--violations", "-v"},
      arity = "4",
      description =
          "The violations to look for. <PARSER> <FOLDER> <REGEXP PATTERN> <NAME> where PARSER"
              + " is one of the values of se.bjurr.violations.lib.reports.Parser (see supported"
              + " formats table in README for the full list).\nExample: -v \"JSHINT\" \".\""
              + " \".*/jshint.xml$\" \"JSHint\"")
  private List<String> violations = new ArrayList<>(); // NOPMD picocli reflection

  @Option(
      names = {"-severity", "-s"},
      description = "Minimum severity level to report.")
  private SEVERITY minSeverity = INFO; // NOPMD picocli reflection

  @Option(
      names = "-show-debug-info",
      description =
          "Please run your command with this parameter and supply output when reporting bugs.")
  private boolean showDebugInfo;

  @Option(
      names = {"-create-comment-with-all-single-file-comments", "-ccwasfc"},
      arity = "1")
  private boolean createCommentWithAllSingleFileComments = false; // NOPMD picocli reflection

  @Option(
      names = {"-create-single-file-comments", "-csfc"},
      arity = "1")
  private boolean createSingleFileComments = true; // NOPMD picocli reflection

  @Option(names = "-keep-old-comments", arity = "1")
  private Boolean keepOldComments = false; // NOPMD picocli reflection

  @Option(
      names = "-comment-template",
      description = "https://github.com/tomasbjerre/violation-comments-lib")
  private String commentTemplate = ""; // NOPMD picocli reflection

  @Option(
      names = {"-pull-request-id", "-prid"},
      required = true)
  private String pullRequestId;

  @Option(
      names = {"-workspace", "-ws"},
      required = true,
      description = "The workspace is typically same as username.")
  private String workspace;

  @Option(
      names = {"-repository-slug", "-rs"},
      required = true)
  private String repositorySlug;

  @Option(names = {"-username", "-u"})
  private String username = ""; // NOPMD picocli reflection

  @Option(
      names = {"-password", "-p"},
      description =
          "You can create an 'application password' in Bitbucket to use here. See"
              + " https://confluence.atlassian.com/bitbucket/app-passwords-828781300.html")
  private String password = ""; // NOPMD picocli reflection

  @Option(
      names = {"-api-token", "-t"},
      description =
          "You can create an 'API token' in Bitbucket to use here. See"
              + " https://support.atlassian.com/bitbucket-cloud/docs/api-tokens/")
  private String apiToken = ""; // NOPMD picocli reflection

  @Option(
      names = {"-comment-only-changed-content", "-cocc"},
      arity = "1",
      description =
          "True if only changed parts of the changed files should be commented. False if all"
              + " findings on the changed files should be commented.")
  private boolean shouldCommentOnlyChangedContent = true; // NOPMD picocli reflection

  @Option(
      names = {"-comment-only-changed-files", "-cocf"},
      arity = "1",
      description =
          "True if only changed files should be commented. False if all findings should be"
              + " commented.")
  private boolean shouldCommentOnlyChangedFiles = true; // NOPMD picocli reflection

  @Option(names = {"-max-number-of-violations", "-max"})
  private Integer maxNumberOfViolations = Integer.MAX_VALUE; // NOPMD picocli reflection

  public void main(final String... args) throws Exception {
    final CommandLine commandLine = new CommandLine(this);
    try {
      commandLine.parseArgs(args);
    } catch (final ParameterException exception) {
      System.out.println(exception.getMessage()); // NOPMD stdout is the CLI output
      exception.getCommandLine().usage(System.out);
      System.exit(1); // NOPMD CLI exit code
      return;
    }

    if (commandLine.isUsageHelpRequested()) {
      commandLine.usage(System.out);
      return;
    }

    if (!this.apiToken.isEmpty() && (!this.username.isEmpty() || !this.password.isEmpty())) {
      System.out.println( // NOPMD stdout is the CLI output
          "API tokens and application passwords cannot be used simultaneously. Specify either"
              + " one of them.");
      System.exit(1); // NOPMD CLI exit code
      return;
    }

    if (this.showDebugInfo) {
      System.out.println( // NOPMD stdout is the CLI output
          "Given parameters:\n"
              + Arrays.asList(args).stream()
                  .map((it) -> it.toString())
                  .collect(Collectors.joining(", "))
              + "\n\nParsed parameters:\n"
              + this.toString());
    }

    ViolationsLogger violationsLogger =
        new ViolationsLogger() {
          @Override
          public void log(final Level level, final String string) {
            System.out.println(level + " " + string); // NOPMD stdout is the CLI output
          }

          @Override
          @SuppressFBWarnings(
              value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE",
              justification =
                  "Printing the stack trace to this CLI's own stdout is the intended behavior")
          public void log(final Level level, final String string, final Throwable t) {
            final StringWriter sw = new StringWriter();
            t.printStackTrace(
                new PrintWriter(sw)); // NOPMD writes to an in-memory buffer, not System.err
            System.out.println( // NOPMD stdout is the CLI output
                level + " " + string + "\n" + sw.toString());
          }
        };
    if (!this.showDebugInfo) {
      violationsLogger = FilteringViolationsLogger.filterLevel(violationsLogger);
    }

    Set<Violation> allParsedViolations = new TreeSet<>();
    for (int i = 0; i < this.violations.size(); i += 4) {
      final List<String> configuredViolation = this.violations.subList(i, i + 4);
      final String reporter = configuredViolation.get(3);
      final Set<Violation> parsedViolations =
          violationsApi() //
              .withViolationsLogger(violationsLogger) //
              .findAll(Parser.valueOf(configuredViolation.get(0))) //
              .inFolder(configuredViolation.get(1)) //
              .withPattern(configuredViolation.get(2)) //
              .withReporter(reporter) //
              .violations();
      if (this.minSeverity != null) {
        allParsedViolations = Filtering.withAtLEastSeverity(allParsedViolations, this.minSeverity);
      }
      allParsedViolations.addAll(parsedViolations);
    }

    System.out.println( // NOPMD stdout is the CLI output
        "PR: " + this.workspace + "/" + this.repositorySlug + "/" + this.pullRequestId);
    final ViolationCommentsToBitbucketCloudApi violationCommentsToBitbucketServerApi =
        new ViolationCommentsToBitbucketCloudApi();
    try {
      if (!this.username.isEmpty()) {
        violationCommentsToBitbucketServerApi //
            .withUsername(this.username) //
            .withPassword(this.password);
      }

      if (!this.apiToken.isEmpty()) {
        violationCommentsToBitbucketServerApi.withApiToken(this.apiToken);
      }

      violationCommentsToBitbucketServerApi //
          .withPullRequestId(this.pullRequestId) //
          .withWorkspace(this.workspace) //
          .withRepositorySlug(this.repositorySlug) //
          .withViolations(allParsedViolations) //
          .withCreateCommentWithAllSingleFileComments(
              this.createCommentWithAllSingleFileComments) //
          .withCreateSingleFileComment(this.createSingleFileComments) //
          .withShouldCommentOnlyChangedContent(this.shouldCommentOnlyChangedContent) //
          .withShouldCommentOnlyChangedFiles(this.shouldCommentOnlyChangedFiles) //
          .withKeepOldComments(this.keepOldComments) //
          .withCommentTemplate(this.commentTemplate) //
          .withMaxNumberOfViolations(this.maxNumberOfViolations) //
          .withViolationsLogger(
              new ViolationsLogger() {
                @Override
                public void log(final Level level, final String string) {
                  System.out.println(level + " " + string); // NOPMD stdout is the CLI output
                }

                @Override
                @SuppressFBWarnings(
                    value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE",
                    justification =
                        "Printing the stack trace to this CLI's own stdout is the intended behavior")
                public void log(final Level level, final String string, final Throwable t) {
                  final StringWriter sw = new StringWriter();
                  t.printStackTrace(
                      new PrintWriter(sw)); // NOPMD writes to an in-memory buffer, not System.err
                  System.out.println( // NOPMD stdout is the CLI output
                      level + " " + string + "\n" + sw.toString());
                }
              }) //
          .toPullRequest();
    } catch (final Exception e) {
      e.printStackTrace(); // NOPMD top-level CLI error handler
    }
  }

  @Override
  public String toString() {
    return "Runner [violations="
        + this.violations
        + ", createCommentWithAllSingleFileComments="
        + this.createCommentWithAllSingleFileComments
        + ", createSingleFileComments="
        + this.createSingleFileComments
        + ", minSeverity="
        + this.minSeverity
        + ", keepOldComments="
        + this.keepOldComments
        + ", commentTemplate="
        + this.commentTemplate
        + ", pullRequestId="
        + this.pullRequestId
        + ", workspace="
        + this.workspace
        + ", repositorySlug="
        + this.repositorySlug
        + ", username="
        + this.username
        + ", password="
        + (this.password != null)
        + ", shouldCommentOnlyChangedContent="
        + this.shouldCommentOnlyChangedContent
        + ", maxNumberOfViolations="
        + this.maxNumberOfViolations
        + "]";
  }
}
