package com.fsck.k9.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.K9MailLib;
import com.fsck.k9.mail.oauth.OAuth2TokenProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by philip on 16/04/2016.
 */
public class AndroidAccountOAuth2TokenStore implements OAuth2TokenProvider {
    private static final String GMAIL_AUTH_TOKEN_TYPE = "oauth2:https://mail.google.com/";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private Map<String,String> authTokens = new HashMap<>();
    private AccountManager accountManager;

    public AndroidAccountOAuth2TokenStore(Context applicationContext) {
        this.accountManager = AccountManager.get(applicationContext);
    }

    @Override
    public String authorizeAPI(String username) throws AuthenticationFailedException {
        return null;
    }

    @Override
    public String getToken(String username) throws AuthenticationFailedException {
        if(authTokens.get(username) == null) {
            Account account = getAccountFromManager(username);
            if (account == null) {
                throw new AuthenticationFailedException("Account not available");
            }
            fetchNewAuthToken(username, account);
        }
        return authTokens.get(username);
    }

    private Account getAccountFromManager(String username) {
        android.accounts.Account[] accounts = accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        for (android.accounts.Account account : accounts) {
            if (account.name.equals(username)) {
                return account;
            }
        }
        return null;
    }

    private void fetchNewAuthToken(String username, Account account) throws AuthenticationFailedException {
        try {
            AccountManagerFuture<Bundle> future = accountManager
                    .getAuthToken(account, GMAIL_AUTH_TOKEN_TYPE, false, null, null);
            Bundle bundle = future.getResult();
            if (bundle.get(AccountManager.KEY_ACCOUNT_NAME).equals(username)) {
                authTokens.put(username, bundle.get(AccountManager.KEY_AUTHTOKEN).toString());
            }
        } catch (Exception e) {
            throw new AuthenticationFailedException(e.getMessage());
        }
    }

    @Override
    public void invalidateToken(String username) {
        accountManager.invalidateAuthToken("com.google", authTokens.get(username));
        authTokens.remove(username);
    }

    public List<String> getAccounts() {
        Account[] accounts = accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        ArrayList<String> accountNames = new ArrayList<>();
        for (Account account: accounts) {
            accountNames.add(account.name);
        }
        return accountNames;
    }
}
