package org.jboss.windup.bootstrap.commands.windup;

import java.util.SortedMap;
import java.util.TreeMap;

import org.jboss.windup.bootstrap.commands.Command;
import org.jboss.windup.bootstrap.commands.CommandPhase;
import org.jboss.windup.bootstrap.commands.CommandResult;
import org.jboss.windup.bootstrap.help.Help;
import org.jboss.windup.bootstrap.help.OptionDescription;

public class DisplayHelpCommand implements Command
{
    @Override
    public CommandResult execute()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: windup [OPTION]... PARAMETER ...\n");
        sb.append("Extendable migration analysis, at your fingertips.\n");
        sb.append("\n");

        sb.append("\nWindup Options:\n");

	SortedMap<String, String> optionNamesAndDescriptions = new TreeMap<String, String>();
        for (OptionDescription option : Help.load().getOptions())
        {
            optionNamesAndDescriptions.put(option.getName(), option.getDescription());
        }

        for (SortedMap.Entry<String, String> entry : optionNamesAndDescriptions.entrySet()) {
            sb.append("--").append(entry.getKey()).append("\n");
            sb.append("\t").append(entry.getValue()).append("\n");
        }

        sb.append("--listTags\n");
        sb.append("\tList all available tags.\n");

        sb.append("--listSourceTechnologies\n");
        sb.append("\tList all available source technologies.\n");

        sb.append("--listTargetTechnologies\n");
        sb.append("\tList all available target technologies.\n");

        sb.append("--discoverPackages\n");
        sb.append("\tList all available packages in the input application (--input must also be specified).\n");

        sb.append("--updateRulesets\n");
        sb.append("\tUpdate the core rulesets to the latest version available.\n");

        sb.append("\nForge Options:\n");

        sb.append("-i, --install GROUP_ID:ARTIFACT_ID[:VERSION]\n");
        sb.append("\tInstall the required addons and exit. ex: `windup -i core-addon-x` or `windup -i org.example.addon:example,1.0.0`\n");

        sb.append("-r, --remove GROUP_ID:ARTIFACT_ID[:VERSION]\n");
        sb.append("\tRemove the required addons and exit. ex: `windup -r core-addon-x` or `windup -r org.example.addon:example,1.0.0`\n");

        sb.append("-l, --list\n");
        sb.append("\tList installed addons and exit.\n");

        sb.append("-a, --addonDir DIR\n");
        sb.append("\tAdd the given directory for use as a custom addon repository.\n");

        sb.append("-m, --immutableAddonDir DIR\n");
        sb.append("\tAdd the given directory for use as a custom immutable addon repository (read only).\n");

        sb.append("-b, --batchMode\n");
        sb.append("\tRun Forge in batch mode and do not prompt for confirmation (exit immediately after running).\n");

        sb.append("-d, --debug\n");
        sb.append("\tRun Forge in debug mode (wait on port 8000 for a debugger to attach).\n");

        sb.append("-h, --help\n");
        sb.append("\tDisplay this help and exit.\n");

        sb.append("-v, --version\n");
        sb.append("\tOutput version information and exit.\n");

        System.out.println(sb.toString());
        return CommandResult.EXIT;
    }

    @Override
    public CommandPhase getPhase()
    {
        return CommandPhase.PRE_CONFIGURATION;
    }
}
