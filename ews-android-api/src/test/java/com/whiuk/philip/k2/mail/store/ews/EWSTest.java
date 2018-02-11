package com.whiuk.philip.k2.mail.store.ews;


import java.net.URI;
import java.net.URISyntaxException;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.PropertySet;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.core.enumeration.property.WellKnownFolderName;
import microsoft.exchange.webservices.data.core.service.folder.Folder;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.core.service.item.Item;
import microsoft.exchange.webservices.data.core.service.schema.ItemSchema;
import microsoft.exchange.webservices.data.credential.ExchangeCredentials;
import microsoft.exchange.webservices.data.credential.WebCredentials;
import microsoft.exchange.webservices.data.property.complex.ItemId;
import microsoft.exchange.webservices.data.search.FindFoldersResults;
import microsoft.exchange.webservices.data.search.FindItemsResults;
import microsoft.exchange.webservices.data.search.FolderView;
import microsoft.exchange.webservices.data.search.ItemView;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(EWSLibRobolectricTestRunner.class)
public class EWSTest {

    @Test
    public void test() throws Exception {

        ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
        ExchangeCredentials credentials = new WebCredentials("email",
                "password");
        service.setCredentials(credentials);
        service.setUrl(new URI("https://outlook.office365.com/EWS/Exchange.asmx"));

        System.out.println("=================");
        System.out.println("Folder list");
        System.out.println("=================");
        FindFoldersResults folderListResults = service.findFolders(WellKnownFolderName.MsgFolderRoot, new FolderView(Integer.MAX_VALUE));

        for (Folder folder : folderListResults.getFolders()) {
            System.out.println(folder.getDisplayName()+" ("+folder.getChildFolderCount()+")");
        }

        System.out.println("=================");
        System.out.println("Inbox email list");
        System.out.println("=================");

        ItemView view = new ItemView(10);
        FindItemsResults<Item> inboxEmailResults = service.findItems(WellKnownFolderName.Inbox, view);
        service.loadPropertiesForItems(inboxEmailResults, PropertySet.FirstClassProperties);

        boolean first = true;
        String firstItemId = null;
        for (Item item : inboxEmailResults.getItems()) {
            if (first) {
                first = false;
                firstItemId = item.getId().getUniqueId();
            }

            System.out.println(item.getSubject()+ " :: " + item.getId());
        }

        System.out.println("=================");
        System.out.println("Latest Email");
        System.out.println("=================");
        PropertySet mimePropertySet = new PropertySet();
        mimePropertySet.add(ItemSchema.MimeContent);
        EmailMessage emailMessage = EmailMessage.bind(service, new ItemId(firstItemId), mimePropertySet);
        System.out.println(emailMessage.getMimeContent().toString());
    }
}
