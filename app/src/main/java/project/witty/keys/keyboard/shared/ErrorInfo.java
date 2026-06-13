package project.witty.keys.keyboard.shared;

import java.util.HashMap;
import java.util.Map;

public class ErrorInfo {
    public enum ErrorType {
        NOT_LOGGED_IN,
        NO_ACTIVE_SUBSCRIPTION,
        SUBSCRIPTION_EXPIRED,
        MANDATE_CANCELLED,
        PAYMENT_FAILED,
        DAILY_LIMIT_REACHED,
    }

    private static final Map<ErrorType, String> ERROR_MESSAGES = new HashMap<>();

    static {
        ERROR_MESSAGES.put(ErrorType.NOT_LOGGED_IN, "You are not logged in. Please log in to continue.");
        ERROR_MESSAGES.put(ErrorType.NO_ACTIVE_SUBSCRIPTION, "You do not have an active subscription. Please subscribe to continue.");
        ERROR_MESSAGES.put(ErrorType.SUBSCRIPTION_EXPIRED, "Your subscription has expired. Please renew your subscription.");
        ERROR_MESSAGES.put(ErrorType.MANDATE_CANCELLED, "Your mandate has been cancelled. Please set up a new mandate.");
        ERROR_MESSAGES.put(ErrorType.PAYMENT_FAILED, "Your payment has failed. Please try again.");
        ERROR_MESSAGES.put(ErrorType.DAILY_LIMIT_REACHED, "Daily credits used. Upgrade for more AI credits.");
    }

    public static String getErrorMessage(ErrorType errorType) {
        return ERROR_MESSAGES.getOrDefault(errorType, "Unknown error");
    }
}
