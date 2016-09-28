package com.fsck.k9.fragment;

import android.app.Activity;
import android.content.CursorLoader;
import android.net.Uri;

import com.fsck.k9.Account;
import com.fsck.k9.activity.MessageReference;
import com.fsck.k9.provider.EmailProvider;
import com.fsck.k9.search.LocalSearch;
import com.fsck.k9.search.SqlQueryBuilder;

import java.util.ArrayList;
import java.util.List;

import static com.fsck.k9.Account.SortType.SORT_ARRIVAL;
import static com.fsck.k9.Account.SortType.SORT_DATE;


public class MLFCursorLoaderFactory {
    public static CursorLoader createCursorLoader(
            String threadId, boolean threadedList, String accountUuid,
            MessageReference activeMessage, Account account, LocalSearch search,
            Activity activity, Account.SortType sortType, boolean sortAscending, boolean sortDateAscending) {
        Uri uri;
        String[] projection;
        boolean needConditions;
        if (threadId != null) {
            uri = Uri.withAppendedPath(EmailProvider.CONTENT_URI, "account/" + accountUuid + "/thread/" + threadId);
            projection = MessageListFragment.PROJECTION;
            needConditions = false;
        } else if (threadedList) {
            uri = Uri.withAppendedPath(EmailProvider.CONTENT_URI, "account/" + accountUuid + "/messages/threaded");
            projection = MessageListFragment.THREADED_PROJECTION;
            needConditions = true;
        } else {
            uri = Uri.withAppendedPath(EmailProvider.CONTENT_URI, "account/" + accountUuid + "/messages");
            projection = MessageListFragment.PROJECTION;
            needConditions = true;
        }

        StringBuilder query = new StringBuilder();
        List<String> queryArgs = new ArrayList<>();
        if (needConditions) {
            boolean selectActive = activeMessage != null && activeMessage.getAccountUuid().equals(accountUuid);

            if (selectActive) {
                query.append("(" + EmailProvider.MessageColumns.UID + " = ? AND " + EmailProvider.SpecialColumns.FOLDER_NAME + " = ?) OR (");
                queryArgs.add(activeMessage.getUid());
                queryArgs.add(activeMessage.getFolderName());
            }

            SqlQueryBuilder.buildWhereClause(account, search.getConditions(), query, queryArgs);

            if (selectActive) {
                query.append(')');
            }
        }

        String selection = query.toString();
        String[] selectionArgs = queryArgs.toArray(new String[0]);

        String sortOrder = buildSortOrder(sortType, sortAscending, sortDateAscending);

        return new CursorLoader(activity, uri, projection, selection, selectionArgs,
                sortOrder);
    }

    private static String buildSortOrder(
            Account.SortType sortType, boolean sortAscending, boolean sortDateAscending) {
        String sortColumn;
        switch (sortType) {
            case SORT_ARRIVAL: {
                sortColumn = EmailProvider.MessageColumns.INTERNAL_DATE;
                break;
            }
            case SORT_ATTACHMENT: {
                sortColumn = "(" + EmailProvider.MessageColumns.ATTACHMENT_COUNT + " < 1)";
                break;
            }
            case SORT_FLAGGED: {
                sortColumn = "(" + EmailProvider.MessageColumns.FLAGGED + " != 1)";
                break;
            }
            case SORT_SENDER: {
                //FIXME
                sortColumn = EmailProvider.MessageColumns.SENDER_LIST;
                break;
            }
            case SORT_SUBJECT: {
                sortColumn = EmailProvider.MessageColumns.SUBJECT + " COLLATE NOCASE";
                break;
            }
            case SORT_UNREAD: {
                sortColumn = EmailProvider.MessageColumns.READ;
                break;
            }
            case SORT_DATE:
            default: {
                sortColumn = EmailProvider.MessageColumns.DATE;
            }
        }

        String sortDirection = (sortAscending) ? " ASC" : " DESC";
        String secondarySort;
        if (sortType == SORT_DATE || sortType == SORT_ARRIVAL) {
            secondarySort = "";
        } else {
            secondarySort = EmailProvider.MessageColumns.DATE + ((sortDateAscending) ? " ASC, " : " DESC, ");
        }

        return sortColumn + sortDirection + ", " + secondarySort + EmailProvider.MessageColumns.ID + " DESC";
    }
}
