package se.bjurr.violations.main;

import static se.bjurr.violations.lib.ViolationsApi.violationsApi;
import static se.bjurr.violations.lib.model.SEVERITY.INFO;
import static se.softhouse.jargo.Arguments.booleanArgument;
import static se.softhouse.jargo.Arguments.enumArgument;
import static se.softhouse.jargo.Arguments.helpArgument;
import static se.softhouse.jargo.Arguments.integerArgument;
import static se.softhouse.jargo.Arguments.optionArgument;
import static se.softhouse.jargo.Arguments.stringArgument;
import static se.softhouse.jargo.CommandLineParser.withArguments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;
import se.bjurr.violations.comments.bitbucketcloud.lib.ViolationCommentsToBitbucketCloudApi;
import se.bjurr.violations.lib.FilteringViolationsLogger;
import se.bjurr.violations.lib.ViolationsLogger;
import se.bjurr.violations.lib.model.SEVERITY;
import se.bjurr.violations.lib.model.Violation;
import se.bjurr.violations.lib.reports.Parser;
import se.bjurr.violations.lib.util.Filtering;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.ParsedArguments;

public class Runner {

  private List<List<String>> violations;
  private boolean createCommentWithAllSingleFileComments;
  private boolean createSingleFileComments;
  private SEVERITY minSeverity;
  private Boolean keepOldComments;
  private String commentTemplate;

  private String pullRequestId;
  private String workspace;
  private String repositorySlug;
  private String username;
  private String password;
  private boolean shouldCommentOnlyChangedContent;
  private boolean shouldCommentOnlyChangedFiles;
  private Integer maxNumberOfViolations;
  private boolean showDebugInfo;

  public void main(final String args[]) throws Exception {
    final Argument<?> helpArgument = helpArgument("-h", "--help");
    final String parsersString =
        Arrays.asList(Parser.values())
            .stream()
            .map((it) -> it.toString())
            .collect(Collectors.joining(", "));
    final Argument<List<List<String>>> violationsArg =
        stringArgument("--violations", "-v")
            .arity(4)
            .repeated()
            .description(
                "The violations to look for. <PARSER> <FOLDER> <REGEXP PATTERN> <NAME> where PARSER is one of: "
                    + parsersString
                    + "\n Example: -v \"JSHINT\" \".\" \".*/jshint.xml$\" \"JSHint\"")
            .build();
    final Argument<SEVERITY> minSeverityArg =
        enumArgument(SEVERITY.class, "-severity", "-s")
            .defaultValue(INFO)
            .description("Minimum severity level to report.")
            .build();
    final Argument<Boolean> showDebugInfo =
        optionArgument("-show-debug-info")
            .description(
                "Please run your command with this parameter and supply output when reporting bugs.")
            .build();

    final Argument<Boolean> createCommentWithAllSingleFileCommentsArg =
        booleanArgument("-create-comment-with-all-single-file-comments", "-ccwasfc")
            .defaultValue(false)
            .build();
    final Argument<Boolean> createSingleFileCommentsArg =
        booleanArgument("-create-single-file-comments", "-csfc").defaultValue(true).build();
    final Argument<Boolean> keepOldCommentsArg =
        booleanArgument("-keep-old-comments").defaultValue(false).build();
    final Argument<String> commentTemplateArg =
        stringArgument("-comment-template")
            .defaultValue("")
            .description("https://github.com/tomasbjerre/violation-comments-lib")
            .build();
    final Argument<String> pullRequestIdArg =
        stringArgument("-pull-request-id", "-prid").required().build();
    final Argument<String> workspaceArg =
        stringArgument("-workspace", "-ws")
            .required()
            .description("The workspace is typically same as username.")
            .build();
    final Argument<String> repositorySlugArg =
        stringArgument("-repository-slug", "-rs").required().build();
    final Argument<String> usernameArg = stringArgument("-username", "-u").defaultValue("").build();
    final Argument<String> passwordArg =
        stringArgument("-password", "-p")
            .defaultValue("")
            .description(
                "You can create an 'application password' in Bitbucket to use here. See https://confluence.atlassian.com/bitbucket/app-passwords-828781300.html")
            .build();
    final Argument<Boolean> shouldCommentOnlyChangedContentArg =
        booleanArgument("-comment-only-changed-content", "-cocc")
            .defaultValue(true)
            .description(
                "True if only changed parts of the changed files should be commented. False if all findings on the changed files should be commented.")
            .build();
    final Argument<Boolean> shouldCommentOnlyChangedFilesArg =
        booleanArgument("-comment-only-changed-files", "-cocf")
            .defaultValue(true)
            .description(
                "True if only changed files should be commented. False if all findings should be commented.")
            .build();
    final Argument<Integer> maxNumberOfViolationsArg =
        integerArgument("-max-number-of-violations", "-max")
            .defaultValue(Integer.MAX_VALUE)
            .build();

    try {
      final ParsedArguments parsed =
          withArguments( //
                  helpArgument, //
                  violationsArg, //
                  minSeverityArg, //
                  showDebugInfo, //
                  createCommentWithAllSingleFileCommentsArg, //
                  createSingleFileCommentsArg, //
                  keepOldCommentsArg, //
                  commentTemplateArg, //
                  pullRequestIdArg, //
                  workspaceArg, //
                  repositorySlugArg, //
                  usernameArg, //
                  passwordArg, //
                  shouldCommentOnlyChangedContentArg, //
                  shouldCommentOnlyChangedFilesArg, //
                  maxNumberOfViolationsArg //
                  ) //
              .parse(args);

      this.violations = parsed.get(violationsArg);
      this.minSeverity = parsed.get(minSeverityArg);
      this.createCommentWithAllSingleFileComments =
          parsed.get(createCommentWithAllSingleFileCommentsArg);
      this.createSingleFileComments = parsed.get(createSingleFileCommentsArg);
      this.keepOldComments = parsed.get(keepOldCommentsArg);
      this.commentTemplate = parsed.get(commentTemplateArg);

      this.pullRequestId = parsed.get(pullRequestIdArg);
      this.workspace = parsed.get(workspaceArg);
      this.repositorySlug = parsed.get(repositorySlugArg);
      this.username = parsed.get(usernameArg);
      this.password = parsed.get(passwordArg);
      this.shouldCommentOnlyChangedContent = parsed.get(shouldCommentOnlyChangedContentArg);
      this.shouldCommentOnlyChangedFiles = parsed.get(shouldCommentOnlyChangedFilesArg);
      this.maxNumberOfViolations = parsed.get(maxNumberOfViolationsArg);
      this.showDebugInfo = parsed.wasGiven(showDebugInfo);
      if (this.showDebugInfo) {
        System.out.println(
            "Given parameters:\n"
                + Arrays.asList(args)
                    .stream()
                    .map((it) -> it.toString())
                    .collect(Collectors.joining(", "))
                + "\n\nParsed parameters:\n"
                + this.toString());
      }

    } catch (final ArgumentException exception) {
      System.out.println(exception.getMessageAndUsage());
      System.exit(1);
    }

    ViolationsLogger violationsLogger =
        new ViolationsLogger() {
          @Override
          public void log(final Level level, final String string) {
            System.out.println(level + " " + string);
          }

          @Override
          public void log(final Level level, final String string, final Throwable t) {
            final StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            System.out.println(level + " " + string + "\n" + sw.toString());
          }
        };
    if (!this.showDebugInfo) {
      violationsLogger = FilteringViolationsLogger.filterLevel(violationsLogger);
    }

    Set<Violation> allParsedViolations = new TreeSet<>();
    for (final List<String> configuredViolation : this.violations) {
      final String reporter = configuredViolation.size() >= 4 ? configuredViolation.get(3) : null;
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

    System.out.println(
        "PR: " + this.workspace + "/" + this.repositorySlug + "/" + this.pullRequestId);
    final ViolationCommentsToBitbucketCloudApi violationCommentsToBitbucketServerApi =
        new ViolationCommentsToBitbucketCloudApi();
    try {
      if (!this.username.isEmpty()) {
        violationCommentsToBitbucketServerApi //
            .withUsername(this.username) //
            .withPassword(this.password);
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
                  System.out.println(level + " " + string);
                }

                @Override
                public void log(final Level level, final String string, final Throwable t) {
                  final StringWriter sw = new StringWriter();
                  t.printStackTrace(new PrintWriter(sw));
                  System.out.println(level + " " + string + "\n" + sw.toString());
                }
              }) //
          .toPullRequest();
    } catch (final Exception e) {
      e.printStackTrace();
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
        + ", projectKey="
        + this.workspace
        + ", repoSlug="
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
