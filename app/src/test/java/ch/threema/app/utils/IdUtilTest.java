package ch.threema.app.utils;

import org.junit.Test;

import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupModelOld;

import static junit.framework.Assert.assertEquals;

public class IdUtilTest {

    @Test
    public void getTempUniqueId() {
        final ContactModel contact1 = ContactModel.create("AAAAAAAA", new byte[32]);
        final ContactModel contact2 = ContactModel.create("BBBBBBBB", new byte[32]);
        final ContactModel contact3 = ContactModel.create("CCCCCCCC", new byte[32]);
        final GroupModelOld group1 = new GroupModelOld();
        group1.setId(1);
        assertEquals(1, IdUtil.getContactTempId(contact1.getIdentity()));
        assertEquals(1, IdUtil.getContactTempId(contact1.getIdentity()));
        assertEquals(2, IdUtil.getContactTempId(contact3.getIdentity()));
        assertEquals(1, IdUtil.getContactTempId(contact1.getIdentity()));
        assertEquals(2, IdUtil.getContactTempId(contact3.getIdentity()));
        assertEquals(3, IdUtil.getGroupTempId(group1));
        assertEquals(4, IdUtil.getContactTempId(contact2.getIdentity()));
    }

}
