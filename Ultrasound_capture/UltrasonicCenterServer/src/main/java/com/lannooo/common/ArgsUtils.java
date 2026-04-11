package com.lannooo.common;

public class ArgsUtils {
    public static boolean checkOptionIn(String option, String[] candidates) {
        for (String opt : candidates) {
            if (opt.equals(option)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean checkFilenameValid(String name) {
        if (name == null) {
            return false;
        }
        // be sure a filename cannot have illegal characters
        return name.matches(AppConstants.FILENAME_REGEX_PATTERN);
    }

    public static boolean checkValidDuration(int duration) {
        return duration == -1 || duration > 0;
    }
}
