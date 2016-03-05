package com.fsck.k9.mail;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.fsck.k9.mail.K9MailLib.LOG_TAG;

public class RecepientAddressParser {
    public static Address[] parseReplyAddresses(Message message) throws MessagingException {
        if (message.getReplyTo().length > 0) {
             return message.getReplyTo();
        } else {
            List<Address> replyToAddresses;
            String[] listPostAddresses = message.getHeader("List-Post");
            replyToAddresses = new ArrayList<Address>(listPostAddresses.length);
            for (int i = 0; i < listPostAddresses.length; i++) {
                if(listPostAddresses[i].equalsIgnoreCase("NO")) {
                   continue;
                }
                int questionMarkIndex = listPostAddresses[i].indexOf("?");
                int closeBracketIndex = listPostAddresses[i].indexOf(">");
                int endPoint = (questionMarkIndex > -1 && questionMarkIndex < closeBracketIndex) ?
                    questionMarkIndex : closeBracketIndex;
                String address = listPostAddresses[i].substring(
                        listPostAddresses[i].indexOf("<mailto:")+("<mailto:".length()),
                        endPoint);
                Log.e(LOG_TAG, "Adding List-Post address: " + address);
                replyToAddresses.addAll(Arrays.asList(Address.parse(address)[0]));
            }
            if (replyToAddresses.size() <= 0) {
                return message.getFrom();
            }
            return replyToAddresses.toArray(new Address[replyToAddresses.size()]);
        }
    }
}
