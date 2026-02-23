package ch.threema.app.utils;

import org.junit.Test;

import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

import static junit.framework.Assert.assertEquals;

public class IdUtilTest {

    @Test
    public void getTempUniqueId() {
        final ContactModel contact1 = ContactModel.create("AAAAAAAA", new byte[32]);
        final ContactModel contact2 = ContactModel.create("BBBBBBBB", new byte[32]);
        final ContactModel contact3 = ContactModel.create("CCCCCCCC", new byte[32]);
        final GroupModel group1 = new GroupModel();
        group1.setId(1);
        assertEquals(1, IdUtil.getTempId(contact1));
        assertEquals(1, IdUtil.getTempId(contact1));
        assertEquals(2, IdUtil.getTempId(contact3));
        assertEquals(1, IdUtil.getTempId(contact1));
        assertEquals(2, IdUtil.getTempId(contact3));
        assertEquals(3, IdUtil.getTempId(group1));
        assertEquals(4, IdUtil.getTempId(contact2));
    }

}
