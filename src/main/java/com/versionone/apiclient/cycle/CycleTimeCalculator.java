package com.versionone.apiclient.cycle;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.versionone.apiclient.Connectors;
import com.versionone.apiclient.EnvironmentContext;
import com.versionone.apiclient.IMetaModel;
import com.versionone.apiclient.IServices;
import com.versionone.apiclient.ModelsAndServices;

public class CycleTimeCalculator {
    private EnvironmentContext _context;

    private IMetaModel _metaModel;
    private IServices _services;

    @SuppressWarnings("static-access")
    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("server").withDescription("Version One server").isRequired().hasArg()
                .withArgName("SERVER").create());
        options.addOption(OptionBuilder.withLongOpt("app").withDescription("Version One app").isRequired().hasArg()
                .withArgName("APP").create());
        options.addOption(OptionBuilder.withLongOpt("user").withDescription("Version One user name").isRequired().hasArg()
                .withArgName("USER").create());
        options.addOption(OptionBuilder.withLongOpt("password").withDescription("Version One password").isRequired().hasArg()
                .withArgName("PASSWORD").create());
        options.addOption(OptionBuilder.withLongOpt("team").withDescription("scrum team name").isRequired().hasArg()
                .withArgName("NAME").create());
        options.addOption(OptionBuilder.withLongOpt("start").withDescription("sprint to start with").isRequired().hasArg()
                .withArgName("NAME").create());
        options.addOption(OptionBuilder.withLongOpt("end").withDescription("sprint to end with").isRequired().hasArg()
                .withArgName("END").create());

        CommandLineParser parser = new GnuParser();
        try {
            CommandLine line = parser.parse(options, args);
            String server = line.getOptionValue("server");
            String app = line.getOptionValue("app");
            String user = line.getOptionValue("user");
            String password = line.getOptionValue("password");
            String team = line.getOptionValue("team");
            String start = line.getOptionValue("start");
            String end = line.getOptionValue("end");
            
            CycleTimeCalculator pct = new CycleTimeCalculator(server, app, user, password);
            pct.calculateCycleTimes(team, start, end);
        } catch (ParseException pe) {
            System.err.println("Error: " + pe.getMessage());
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(new PrintWriter(System.err, true), 200, CycleTimeCalculator.class.getSimpleName(),
                    "Arguments:", options, 4, 4, null, true);
        }
    }

    public CycleTimeCalculator(String server, String app, String user, String password) throws Exception {
        Credentials credentials = new Credentials(user, password);
        Urls urls = new Urls(server, app);
        Connectors connectors = new Connectors(urls, credentials);
        ModelsAndServices modelsAndServices = new ModelsAndServices(connectors);
        _context = new EnvironmentContext(modelsAndServices);
        _metaModel = _context.getMetaModel();
        _services = _context.getServices();
    }

    private void calculateCycleTimes(String team, String start, String end) throws Exception {
        AssetResult scrumTeam = findTeam(team);
        int startSprint = Integer.valueOf(start);
        int endSprint = Integer.valueOf(end);

        HashSet<String> sprintSet = new HashSet<String>();

        for (int i = startSprint; i <= endSprint; i++) {
            sprintSet.add(String.valueOf(i));
        }

        List<AssetResult> allSprints = findSprints();
        List<AssetResult> filterSprints = new ArrayList<AssetResult>();
        Map<String, AssetResult> sprintMap = new HashMap<String, AssetResult>(allSprints.size());
        for (AssetResult sprint : allSprints) {
            String sprintName = sprint.value("Name").toString();
            if (sprintSet.contains(sprintName)) {
                filterSprints.add(sprint);
            }
            sprintMap.put(sprint.getId(), sprint);
        }

        List<AssetResult> allStatus = findStatus();
        Map<String, AssetResult> statusMap = new HashMap<String, AssetResult>(allStatus.size());
        for (AssetResult status : allStatus) {
            statusMap.put(status.getId(), status);
        }

        System.out.println("Number,Status,Name,Sprint,Estimate,CycleTime");

        List<AssetResult> stories = findTeamBacklogsAndDefects(scrumTeam, filterSprints);
        for (AssetResult story : stories) {
            System.out.print(story.value("Number"));
            String statusId = story.value("Status").toString();
            if (!statusId.equals("NULL")) {
                System.out.print("," + statusMap.get(statusId).value("Name"));
            } else {
                System.out.print(",");

            }
            System.out.print(",\"" + story.value("Name") + "\"");
            String sprintId = story.value("Timebox").toString();
            System.out.print("," + sprintMap.get(sprintId).value("Name"));
            System.out.print("," + story.value("Estimate"));
            Object[] history = story.values("History");
            int cycleDays = analyzeHistory(sprintMap, statusMap, history);
            System.out.println("," + cycleDays);
        }
    }

    protected int analyzeHistory(Map<String, AssetResult> sprintMap, Map<String, AssetResult> statusMap, Object[] history)
            throws Exception {
        Date lastMoveToSprint = null;
        Date testingCompleteOrDoneDone = null;
        List<AssetResult> historyAssets = findHistory(history);
        Collections.sort(historyAssets, new HistoryComparator());
        for (AssetResult historyAsset : historyAssets) {

            Date changeDate = (Date) historyAsset.value("ChangeDate");
            String statusId = historyAsset.value("Status").toString();
            if (!statusId.equals("NULL")) {
                statusId = statusId.substring(0, statusId.lastIndexOf(":")); // status id from history contains moment
            }
            if (statusMap.containsKey(statusId)) {
                Object statusName = statusMap.get(statusId).value("Name");
                if (statusName.toString().equals("Testing Complete")) {
                    if (isEndOfCycle(lastMoveToSprint, testingCompleteOrDoneDone, changeDate)) {
                        testingCompleteOrDoneDone = changeDate;
                    }
                } else if (statusName.toString().equals("Done Done")) {
                    if (isEndOfCycle(lastMoveToSprint, testingCompleteOrDoneDone, changeDate)) {
                        testingCompleteOrDoneDone = changeDate;
                    }
                }
            }

            String sprintId = historyAsset.value("Timebox").toString();
            if (!sprintId.equals("NULL")) {
                sprintId = sprintId.substring(0, sprintId.lastIndexOf(":")); // sprint id from history contains moment
            }
            if (sprintMap.containsKey(sprintId)) {
                Object sprintName = sprintMap.get(sprintId).value("Name");
                try {
                    Integer.parseInt(sprintName.toString());
                    if (lastMoveToSprint == null) {
                        lastMoveToSprint = changeDate;
                    }
                } catch (NumberFormatException nfe) {
                    lastMoveToSprint = null;
                }
            }
        }

        int cycleDays = countDaysBetween(lastMoveToSprint, testingCompleteOrDoneDone);
        return cycleDays;
    }

    private int countDaysBetween(Date lastMoveToSprint, Date testingCompleteOrDoneDone) {
        if (lastMoveToSprint == null) {
            return 0;
        }

        Date end = (testingCompleteOrDoneDone == null) ? new Date() : testingCompleteOrDoneDone;
        Calendar cal = Calendar.getInstance();
        cal.setLenient(true); // We want year to rollover as we iterate

        cal.setTime(end);
        int endDay = makeDayRepresentation(cal);

        int days = 1; // Always count the start, even if it is on the weekend. If the end is on the weekend it doesn't get counted.
        cal.setTime(lastMoveToSprint);
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                days++;
            }
        } while (makeDayRepresentation(cal) < endDay);
        return days;
    }

    protected int makeDayRepresentation(Calendar cal) {
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
    }

    protected boolean isEndOfCycle(Date lastMoveToSprint, Date testingCompleteOrDoneDone, Date changeDate) {
        return testingCompleteOrDoneDone == null && lastMoveToSprint != null && changeDate.after(lastMoveToSprint);
    }

    private List<AssetResult> findHistory(Object... ids) throws Exception {
        Object storyId = ids[0];
        Object[] moments = new Object[ids.length];
        for (int i = 0; i < ids.length; i++) {
            String[] idArray = ids[i].toString().split(":");
            moments[i] = idArray[2];
        }
        AssetSelector selector = new AssetSelector(_metaModel, "PrimaryWorkitem", "ID", "Name", "Team", "Timebox", "Status",
                "ChangeDate", "Moment");
        selector.filterAttributeEquals("Moment", moments);
        selector.filterAttributeEquals("ID", storyId);
        List<AssetResult> results = selector.retrieve(_services);
        return results;
    }

    private List<AssetResult> findTeamBacklogsAndDefects(AssetResult team, List<AssetResult> sprints) throws Exception {
        AssetSelector selector = new AssetSelector(_metaModel, "PrimaryWorkitem", "Name", "Number", "Status", "Estimate", "Team",
                "Timebox", "History");
        selector.filterAttributeEquals("Team", team.getId());
        Object[] sprintIds = new Object[sprints.size()];
        for (int i = 0; i < sprints.size(); i++) {
            sprintIds[i] = sprints.get(i).getId();
        }
        selector.filterAttributeEquals("Timebox", sprintIds);
        List<AssetResult> results = selector.retrieve(_services);
        return results;
    }

    private List<AssetResult> findSprints() throws Exception {
        AssetSelector selector = new AssetSelector(_metaModel, "Timebox", "BeginDate", "EndDate", "Name");
        List<AssetResult> results = selector.retrieve(_services);
        return results;
    }

    private List<AssetResult> findStatus() throws Exception {
        AssetSelector selector = new AssetSelector(_metaModel, "StoryStatus", "Name");
        List<AssetResult> results = selector.retrieve(_services);
        return results;
    }

    public AssetResult findTeam(String teamName) throws Exception {
        AssetSelector selector = new AssetSelector(_metaModel, "Team", "Name");
        selector.filterAttributeEquals("Name", teamName);
        List<AssetResult> results = selector.retrieve(_services);

        AssetResult team = results.get(0);
        return team;
    }
}