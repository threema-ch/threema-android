/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.utils

import android.content.res.Resources
import ch.threema.app.R
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.parameter.*
import ezvcard.property.*
import ezvcard.util.GeoUri
import ezvcard.util.PartialDate
import ezvcard.util.TelUri
import ezvcard.util.UtcOffset
import org.junit.Assert
import org.junit.Test
import java.util.*

class VCardExtractorTest {

    private val extractor: VCardExtractor = VCardExtractor(java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Locale.US), TestResources())

    @Test
    fun testStructuredNameText() {
        val names = getStructuredNames()

        assertText(null, names[0], true)
        assertText("Dr GivenName AdditionalName FamilyName, MD", names[0])
        assertText("GivenName FamilyName", names[1])
        assertText("Dr FamilyName", names[2])
        assertText("GivenName", names[3])
        assertText(", MD", names[4])
        assertText(", MD Suff", names[5])
        assertText(", MD Suff", names[6])
        assertText("FamilyName", names[7])
        assertText("AdditionalName", names[8])
    }

    @Test
    fun testAddressesText() {
        val addresses = getAddresses()

        assertText("WorkStrasse 42\nPostalCode Ort\nRegion", addresses[0])
        assertText("OnlyStreet 42", addresses[7])
        assertText("OnlyRegion", addresses[8])
        assertText("OnlyLocality", addresses[9])
        assertText("OnlyPostalCode", addresses[10])
        assertText("ExtendedAddress", addresses[11])
    }

    @Test
    fun testAgentText() {
        val agents = getAgents()

        assertText("http://www.linkedin.com/BobSmith", agents[0])

        // Check that the first line matches. The other lines must also match, but can appear in any order
        extractor.getText(agents[1]).let {
            Assert.assertEquals("Agent Vcard", it.lines().first())
            val expected = arrayOf("012345678", "OrgDirectory", "http://www.linkedin.com/BobSmith")
            Assert.assertArrayEquals(expected, it.lines().drop(1).toTypedArray().also(Array<String>::sort))
        }

        // Check that the first line matches. The other lines must also match, but can appear in any order
        extractor.getText(agents[2]).let {
            Assert.assertEquals("Outer Agent", it.lines().first())
            val expected = arrayOf("012345678", "Inner Agent", "Middle Agent", "OrgDirectory",
                    "http://www.inneragent.com", "http://www.linkedin.com/BobSmith", "http://www.middleagent.com")
            Assert.assertArrayEquals(expected, it.lines().drop(1).toTypedArray().also(Array<String>::sort))
        }
    }

    @Test
    fun testLogoText() {
        val logos = getLogos()
        assertText("http://www.website.com/logo.png", logos[0])
        assertText("", logos[1])
    }

    @Test
    fun testPhotoText() {
        val photos = getPhotos()
        assertText("http://www.website.com/photo.png", photos[0])
        assertText("", photos[1])
    }

    @Test
    fun testKeyText() {
        val keys = getKeys()
        assertText("http://www.mywebsite.com/my-public-key.pgp", keys[0])
        assertText("OPENPGP4FPR:ABAF11C65A2970B130ABE3C479BE3E4300411886", keys[1])
        assertText("Key", keys[2])
        assertText("Key", keys[3])
    }

    @Test
    fun testSoundText() {
        val (first, second) = getSounds()
        assertText("http://www.mywebsite.com/my-name.ogg", first)
        assertText("", second)
    }

    @Test
    fun testClientPidMapText() {
        assertText("PID Map", getClientPidMap())
    }

    @Test
    fun testDateOrTimePropertyText() {
        val dates = getDateOrTimeProperties()
        assertText("1/1/70", dates[0])
        assertText("1/2/70", dates[1])
        assertText("1/3/70", dates[2])
        assertText("2/4/00", dates[3])
        assertText("2/5/00", dates[4])
        assertText("2/6/00", dates[5])
        assertText("4/7/01", dates[6])
        assertText("4/8/01", dates[7])
        assertText("4/9/01", dates[8])
    }

    @Test
    fun testGendersText() {
        assertText("F", Gender.female())
        assertText("M", Gender.male())
        assertText("N", Gender.none())
        assertText("O", Gender.other())
        assertText("U", Gender.unknown())
    }

    @Test
    fun testGeoText() {
        val geos = getGeos()
        assertText("geo:40.7127,-74.0059", geos[0])
        assertText("geo:40.1,-70.2", geos[1])
    }

    @Test
    fun testImppText() {
        val impp = getImpp()
        assertText("johndoe@ma.il", impp[0])
        assertText("skypeHandle", impp[1])
        assertText("sipHandle", impp[2])
        assertText("handle", impp[3])
    }

    @Test
    fun testListPropertiesText() {
        val listProperties = getListProperties()
        assertText("Developer, Java coder, Android Developer", listProperties[0])
        assertText("Ricky, Bobby, Bob", listProperties[1])
        assertText("Threema, Android Team", listProperties[2])
        assertText(null, listProperties[3])
        assertText(null, listProperties[4])
        assertText("Category", listProperties[5])
    }

    @Test
    fun testPlacePropertiesText() {
        val places = getPlaceProperties()
        assertText("Maida Vale, London, United Kingdom", places[0])
        assertText("geo:51.5274,-0.1899", places[1])
        assertText("http://en.wikipedia.org/wiki/Maida_Vale", places[2])
        assertText("Maida, London, United Kingdom", places[3])
        assertText("geo:20.5274,-5.1899", places[4])
        assertText("http://en.wikipedia.org/wiki/Maida", places[5])
        assertText(null, places[6])
        assertText("geo:0.1,2.3", places[7])
    }

    @Test
    fun testRelatedText() {
        val relatives = getRelated()
        assertText("bob.smith@example.com", relatives[0])
        assertText("Edna Smith", relatives[1])
        assertText("urn:uuid:03a0e51f-d1aa-4385-8a53-e29025acd8af", relatives[2])
        assertText("AcquaintanceRelated", relatives[3])
        assertText("AgentRelated", relatives[4])
        assertText("ChildRelated", relatives[5])
        assertText("SeveralRelated", relatives[6])
        assertText("text@ma.il", relatives[7])
    }

    @Test
    fun testRevisionText() {
        assertText(null, Revision(Date())) // revisions are not shown
    }

    @Test
    fun testTextPropertyText() {
        val texts = getTextProperties()
        assertText("classificationText", texts[0])
        assertText("emailText", texts[1])
        assertText("expertiseText", texts[2])
        assertText("formattedNameText", texts[3])
        assertText("hobbyText", texts[4])
        assertText("interestText", texts[5])
        assertText("kindText", texts[6])
        assertText("labelText", texts[7])
        assertText("languageText", texts[8])
        assertText("mailerText", texts[9])
        assertText("noteText", texts[10])
        assertText("productIdText", texts[11])
        assertText("VCARD", texts[12])
        assertText("rawPropertyValue", texts[13])
        assertText("roleText", texts[14])
        assertText("sortStringText", texts[15])
        assertText("sourceDisplayTextText", texts[16])
        assertText("titleText", texts[17])
        assertText("uriPropertyText", texts[18])
    }

    @Test
    fun testTelephoneText() {
        val telephones = getTelephones()
        assertText("telephoneText", telephones[0])
        assertText("tel:+1-800-555-9876;ext=111", telephones[1])
        assertText("carPhone", telephones[2])
        assertText("bbsPhone", telephones[3])
        assertText("FaxPhone", telephones[4])
        assertText("homePhone", telephones[5])
        assertText("severalTypesPhone", telephones[6])
    }

    @Test
    fun testTimezoneText() {
        val timezones = getTimezones()
        assertText("America/New_York, -0500", timezones[0])
        assertText("+0530", timezones[1])
    }

    @Test
    fun testXmlText() {
        val xmls = getXmls()
        assertText("XML", xmls[0])
        assertText("XML", xmls[1])
    }

    @Test
    fun testStructuredNameDescription() {
        val names = getStructuredNames()

        assertDescription("", names[0])
        assertDescription("", names[1])
        assertDescription("", names[2])
        assertDescription("", names[3])
        assertDescription("", names[4])
        assertDescription("", names[5])
        assertDescription("", names[6])
        assertDescription("", names[7])
        assertDescription("", names[8])
    }

    @Test
    fun testAddressesDescription() {
        val addresses = getAddresses()

        assertDescription("Work", addresses[0])
        assertDescription("Other", addresses[1])
        assertDescription("Home", addresses[2])
        assertDescription("Other", addresses[3])
        assertDescription("Other", addresses[4])
        assertDescription("Other", addresses[5])
        assertDescription("Other", addresses[6])
        assertDescription("Other", addresses[7])
        assertDescription("Home", addresses[8])
        assertDescription("Home", addresses[9])
        assertDescription("Home", addresses[10])
        assertDescription("Home, Work, Other", addresses[11])
        assertDescription("Home, Work, Other", addresses[12])
    }

    @Test
    fun testAgentDescription() {
        val agents = getAgents()
        assertDescription("", agents[0])
        assertDescription("", agents[1])
        assertDescription("", agents[2])
    }

    @Test
    fun testLogoDescription() = getLogos().forEach { assertDescription("", it) }

    @Test
    fun testPhotoDescription() = getPhotos().forEach { assertDescription("", it) }

    @Test
    fun testKeyDescription() {
        val keys = getKeys()
        assertDescription("", keys[0])
        assertDescription("", keys[1])
        assertDescription("", keys[2])
        assertDescription("", keys[3])
    }

    @Test
    fun testSoundDescription() {
        val (first, second) = getSounds()
        assertDescription("", first)
        assertDescription("", second)
    }

    @Test
    fun testClientPidMapDescription() {
        assertDescription("", getClientPidMap())
    }

    @Test
    fun testDateOrTimePropertyDescription() {
        val dates = getDateOrTimeProperties()
        assertDescription("Anniversary", dates[0])
        assertDescription("Birthday", dates[1])
        assertDescription("Other", dates[2])
        assertDescription("Anniversary", dates[3])
        assertDescription("Birthday", dates[4])
        assertDescription("Other", dates[5])
        assertDescription("Anniversary", dates[6])
        assertDescription("Birthday", dates[7])
        assertDescription("Other", dates[8])
    }

    @Test
    fun testGendersDescription() {
        assertDescription("", Gender.female())
        assertDescription("", Gender.male())
        assertDescription("", Gender.none())
        assertDescription("", Gender.other())
        assertDescription("", Gender.unknown())
    }

    @Test
    fun testGeoDescription() {
        val geos = getGeos()
        assertDescription("", geos[0])
        assertDescription("", geos[1])
    }

    @Test
    fun testImppDescription() {
        val impp = getImpp()
        assertDescription("aim", impp[0])
        assertDescription("skype", impp[1])
        assertDescription("sip", impp[2])
        assertDescription("protocol", impp[3])
    }

    @Test
    fun testListPropertiesDescription() {
        val listProperties = getListProperties()
        assertDescription("", listProperties[0])
        assertDescription("Nickname", listProperties[1])
        assertDescription("Organization", listProperties[2])
        assertDescription("", listProperties[3])
        assertDescription("", listProperties[4])
        assertDescription("", listProperties[5])
    }

    @Test
    fun testPlacePropertiesDescription() {
        val places = getPlaceProperties()
        assertDescription("", places[0])
        assertDescription("", places[1])
        assertDescription("", places[2])
        assertDescription("", places[3])
        assertDescription("", places[4])
        assertDescription("", places[5])
        assertDescription("", places[6])
        assertDescription("", places[7])
    }

    @Test
    fun testRelatedDescription() {
        val relatives = getRelated()
        assertDescription("", relatives[0])
        assertDescription("", relatives[1])
        assertDescription("", relatives[2])
        assertDescription("Custom", relatives[3])
        assertDescription("Custom", relatives[4])
        assertDescription("Child", relatives[5])
        assertDescription("Custom, Custom, Friend", relatives[6])
        assertDescription("", relatives[7])
    }

    @Test
    fun testRevisionDescription() {
        assertDescription("", Revision(Date())) // revisions are not shown
    }

    @Test
    fun testTextPropertyDescription() {
        val texts = getTextProperties()
        assertDescription("", texts[0])
        assertDescription("Other", texts[1])
        assertDescription("", texts[2])
        assertDescription("", texts[3])
        assertDescription("", texts[4])
        assertDescription("", texts[5])
        assertDescription("", texts[6])
        assertDescription("", texts[7])
        assertDescription("", texts[8])
        assertDescription("", texts[9])
        assertDescription("", texts[10])
        assertDescription("", texts[11])
        assertDescription("", texts[12])
        assertDescription("rawPropertyName", texts[13])
        assertDescription("", texts[14])
        assertDescription("", texts[15])
        assertDescription("", texts[16])
        assertDescription("", texts[17])
        assertDescription("", texts[18])
    }

    @Test
    fun testTelephoneDescription() {
        val telephones = getTelephones()
        assertDescription("", telephones[0])
        assertDescription("", telephones[1])
        assertDescription("Car", telephones[2])
        assertDescription("Other", telephones[3])
        assertDescription("Other Fax", telephones[4])
        assertDescription("Home", telephones[5])
        assertDescription("Other, Mobile, ISDN", telephones[6])
    }

    @Test
    fun testTimezoneDescription() {
        val timezones = getTimezones()
        assertDescription("", timezones[0])
        assertDescription("", timezones[1])
    }

    @Test
    fun testXmlDescription() {
        val xmls = getXmls()
        assertDescription("", xmls[0])
        assertDescription("", xmls[1])
    }

    @Test
    fun testAndroidExample() {
        val properties = getAndroidExampleVCard().properties.toList()

        assertText("John Middle Doe", properties[0])
        assertDescription("", properties[0])
        assertText("PhoneFirst", properties[1])
        assertDescription("PHONETIC-FIRST-NAME", properties[1])
        assertText("PhoneMiddle", properties[2])
        assertDescription("PHONETIC-MIDDLE-NAME", properties[2])
        assertText("PhoneLast", properties[3])
        assertDescription("PHONETIC-LAST-NAME", properties[3])
        assertText("SpouseText", properties[4])
        assertDescription("", properties[4])
        assertText("00111111111", properties[5])
        assertDescription("SIP", properties[5])
        assertText("078-464-6564", properties[6])
        assertDescription("Home", properties[6])
        assertText("mail@example.com", properties[7])
        assertDescription("Other", properties[7])
        assertText("home@example.com", properties[8])
        assertDescription("Home", properties[8])
        assertText("work@example.com", properties[9])
        assertDescription("Work", properties[9])
        assertText("mobile@example.com", properties[10])
        assertDescription("Other", properties[10])
        assertText("Street, OH, USA", properties[11])
        assertDescription("Other", properties[11])
        assertText("HomeStreet 10", properties[12])
        assertDescription("Home", properties[12])
        assertText("WorkStreet 42", properties[13])
        assertDescription("Work", properties[13])
        assertText("OtherStreet 10", properties[14])
        assertDescription("Home", properties[14])
        assertText("CompanyName, DepartmentName", properties[15])
        assertDescription("Organization", properties[15])
        assertText("TitleText", properties[16])
        assertDescription("", properties[16])
        assertText("example.com", properties[17])
        assertDescription("", properties[17])
        assertText("", properties[18])
        assertDescription("", properties[18])
        assertText("NotesText", properties[19])
        assertDescription("", properties[19])
        assertText("1/26/22", properties[20])
        assertDescription("Birthday", properties[20])
    }

    private fun assertText(expected: String?, property: VCardProperty, ignoreName: Boolean = false) {
        if (expected == null) {
            Assert.assertThrows(VCardExtractor.VCardExtractionException::class.java) { extractor.getText(property, ignoreName) }
        } else {
            Assert.assertEquals(expected, extractor.getText(property, ignoreName))
        }
    }

    private fun assertDescription(expected: String?, property: VCardProperty) {
        Assert.assertEquals(expected, extractor.getDescription(property))
    }

    private fun getAddresses(): List<Address> = listOf(
            Address().apply {
                streetAddress = "WorkStrasse 42"
                locality = "Ort"
                region = "Region"
                postalCode = "PostalCode"
                types.add(AddressType.WORK)
            },
            Address().apply {
                streetAddress = "DomStrasse 42"
                locality = "Ort"
                region = "Region"
                postalCode = "PostalCode"
                types.add(AddressType.DOM)
            },
            Address().apply {
                streetAddress = "HomeStrasse 42"
                locality = "Ort"
                region = "Region"
                postalCode = "PostalCode"
                types.add(AddressType.HOME)
            },
            Address().apply {
                streetAddress = "INTLStrasse 42"
                locality = "Ort"
                region = "Region"
                postalCode = "PostalCode"
                types.add(AddressType.INTL)
            },
            Address().apply {
                streetAddress = "ParcelStrasse 42"
                locality = "Ort"
                region = "Region"
                postalCode = "PostalCode"
                types.add(AddressType.PARCEL)
            },
            Address().apply {
                streetAddress = "PostalStrasse 42"
                locality = "Ort"
                region = "Region"
                postalCode = "PostalCode"
                types.add(AddressType.POSTAL)
            },
            Address().apply {
                streetAddress = "PrefStrasse 42"
                locality = "Ort"
                region = "Region"
                postalCode = "PostalCode"
                types.add(AddressType.PREF)
            },
            Address().apply {
                streetAddress = "OnlyStreet 42"
            },
            Address().apply {
                region = "OnlyRegion"
                types.add(AddressType.HOME)
            },
            Address().apply {
                locality = "OnlyLocality"
                types.add(AddressType.HOME)
            },
            Address().apply {
                postalCode = "OnlyPostalCode"
                types.add(AddressType.HOME)
            },
            Address().apply {
                extendedAddress = "ExtendedAddress"
                types.add(AddressType.HOME)
                types.add(AddressType.WORK)
                types.add(AddressType.INTL)
            },
            Address().apply {
                extendedAddress = "ExtendedAddress"
                types.add(AddressType.HOME)
                types.add(AddressType.WORK)
                types.add(AddressType.INTL)
                types.add(AddressType.PARCEL)
                types.add(AddressType.PREF)
                types.add(AddressType.POSTAL)
                types.add(AddressType.POSTAL)
            }
    )

    private fun getAgents(): List<Agent> = listOf(
            Agent("http://www.linkedin.com/BobSmith"),
            Agent(VCard().apply {
                setFormattedName("Agent Vcard")
                addTelephoneNumber("012345678")
                addUrl("http://www.linkedin.com/BobSmith")
                addOrgDirectory("OrgDirectory")
            }),
            Agent(VCard().apply {
                setFormattedName("Outer Agent")
                addTelephoneNumber("012345678")
                addUrl("http://www.linkedin.com/BobSmith")
                addOrgDirectory("OrgDirectory")
                agent = Agent(VCard().apply {
                    setFormattedName("Middle Agent")
                    addUrl("http://www.middleagent.com")
                    agent = Agent(VCard().apply {
                        setFormattedName("Inner Agent")
                        addUrl("http://www.inneragent.com")
                    })
                })
            })
    )

    private fun getLogos(): List<Logo> = listOf(
            Logo("http://www.website.com/logo.png", ImageType.PNG),
            Logo(byteArrayOf(12, 59, 103, 12, 14, 51, 16, 17), ImageType.GIF)
    )

    private fun getPhotos(): List<Photo> = listOf(
            Photo("http://www.website.com/photo.png", ImageType.PNG),
            Photo(byteArrayOf(12, 59, 103, 12, 14, 51, 16, 17), ImageType.JPEG),
    )

    private fun getKeys(): List<Key> = listOf(
            Key("http://www.mywebsite.com/my-public-key.pgp", KeyType.PGP),
            Key("OPENPGP4FPR:ABAF11C65A2970B130ABE3C479BE3E4300411886", null),
            Key(byteArrayOf(12, 59, 103, 12, 14, 51, 16, 17), KeyType.X509),
            Key().apply { setText("plaintextkey", KeyType.GPG) },
    )

    private fun getSounds(): List<Sound> = listOf(
            Sound("http://www.mywebsite.com/my-name.ogg", SoundType.OGG),
            Sound(byteArrayOf(12, 59, 103, 12, 14, 51, 16, 17), SoundType.AAC)
    )

    private fun getClientPidMap(): ClientPidMap = ClientPidMap.random(2)

    private fun getDateOrTimeProperties(): List<DateOrTimeProperty> = listOf(
            Anniversary(PartialDate.builder().date(1).month(1).build()),
            Birthday(PartialDate.builder().date(2).month(1).build()),
            Deathdate(PartialDate.builder().date(3).month(1).build()),
            Anniversary(PartialDate.builder().date(4).month(2).year(2000).build()),
            Birthday(PartialDate.builder().date(5).month(2).year(2000).build()),
            Deathdate(PartialDate.builder().date(6).month(2).year(2000).build()),
            Anniversary(Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, 2001)
                set(Calendar.MONTH, 3)
                set(Calendar.DAY_OF_MONTH, 7)
                set(Calendar.HOUR, 1)
            }.time),
            Birthday(Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, 2001)
                set(Calendar.MONTH, 3)
                set(Calendar.DAY_OF_MONTH, 8)
                set(Calendar.HOUR, 2)
            }.time),
            Deathdate(Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, 2001)
                set(Calendar.MONTH, 3)
                set(Calendar.DAY_OF_MONTH, 9)
                set(Calendar.HOUR, 3)
            }.time),
            Anniversary("Anniversary long ago"),
            Birthday("Birthday long ago"),
            Deathdate("Deathdate long ago")
    )

    private fun getGeos(): List<Geo> = listOf(
            Geo(40.7127, -74.0059),
            Geo(GeoUri.parse("geo:40.1,-70.2"))
    )

    private fun getImpp(): List<Impp> = listOf(
            Impp("aim:johndoe@ma.il"),
            Impp.skype("skypeHandle"),
            Impp.sip("sipHandle"),
            Impp("protocol", "handle")
    )

    private fun getListProperties(): List<ListProperty<*>> = listOf(
            Categories().apply {
                values.apply {
                    add("Developer")
                    add("Java coder")
                    add("Android Developer")
                }
            },
            Nickname().apply {
                values.apply {
                    add("Ricky")
                    add("Bobby")
                    add("Bob")
                }
            },
            Organization().apply {
                values.apply {
                    add("Threema")
                    add("Android Team")
                }
            },
            Categories(),
            Categories().apply {
                values.add("")
            },
            Categories().apply {
                values.add("Category")
            }
    )

    private fun getPlaceProperties(): List<PlaceProperty> = listOf(
            Birthplace("Maida Vale, London, United Kingdom"),
            Birthplace(51.5274, -0.1899),
            Birthplace().apply { uri = "http://en.wikipedia.org/wiki/Maida_Vale" },
            Deathplace("Maida, London, United Kingdom"),
            Deathplace(20.5274, -5.1899),
            Deathplace().apply { uri = "http://en.wikipedia.org/wiki/Maida" },
            Birthplace(),
            Birthplace().apply { geoUri = GeoUri.parse("geo:0.1,2.3") }
    )

    private fun getRelated(): List<Related> = listOf(
            Related.email("bob.smith@example.com"),
            Related().apply { text = "Edna Smith" },
            Related("urn:uuid:03a0e51f-d1aa-4385-8a53-e29025acd8af"),
            Related().apply { text = "AcquaintanceRelated"; types.add(RelatedType.ACQUAINTANCE) },
            Related().apply { text = "AgentRelated"; types.add(RelatedType.AGENT) },
            Related().apply { text = "ChildRelated"; types.add(RelatedType.CHILD) },
            Related().apply {
                text = "SeveralRelated"
                types.add(RelatedType.CO_WORKER)
                types.add(RelatedType.COLLEAGUE)
                types.add(RelatedType.FRIEND)
            },
            Related().apply {
                text = "Text"
                uri = "text@ma.il" // sets the text to null
            }
    )

    private fun getTextProperties(): List<TextProperty> = listOf(
            Classification("classificationText"),
            Email("emailText"),
            Expertise("expertiseText"),
            FormattedName("formattedNameText"),
            Hobby("hobbyText"),
            Interest("interestText"),
            Kind("kindText"),
            Label("labelText"),
            Language("languageText"),
            Mailer("mailerText"),
            Note("noteText"),
            ProductId("productIdText"),
            Profile(),
            RawProperty("rawPropertyName", "rawPropertyValue"),
            Role("roleText"),
            SortString("sortStringText"),
            SourceDisplayText("sourceDisplayTextText"),
            Title("titleText"),
            UriProperty("uriPropertyText")
    )

    private fun getStructuredNames(): List<StructuredName> = listOf(
            StructuredName().apply {
                family = "FamilyName"
                given = "GivenName"
                prefixes.add("Dr")
                suffixes.add("MD")
                additionalNames.add("AdditionalName")
            },
            StructuredName().apply {
                family = "FamilyName"
                given = "GivenName"
            },
            StructuredName().apply {
                family = "FamilyName"
                prefixes.add("Dr")
            },
            StructuredName().apply {
                given = "GivenName"
            },
            StructuredName().apply {
                suffixes.add("MD")
            },
            StructuredName().apply {
                suffixes.add("MD Suff")
            }, StructuredName().apply {
        suffixes.add("MD")
        suffixes.add("Suff")
    },
            StructuredName().apply {
                family = "FamilyName"
            },
            StructuredName().apply {
                additionalNames.add("AdditionalName")
            }
    )

    private fun getTelephones(): List<Telephone> = listOf(
            Telephone("telephoneText"),
            Telephone(TelUri.Builder("+1-800-555-9876").extension("111").build()),
            Telephone("carPhone").apply { pref = 1; types.add(TelephoneType.CAR) },
            Telephone("bbsPhone").apply { pref = 2; types.add(TelephoneType.BBS) },
            Telephone("FaxPhone").apply { pref = 3; types.add(TelephoneType.FAX) },
            Telephone("homePhone").apply { types.add(TelephoneType.HOME) },
            Telephone("severalTypesPhone").apply {
                types.add(TelephoneType.MODEM)
                types.add(TelephoneType.CELL)
                types.add(TelephoneType.ISDN)
            }
    )

    private fun getTimezones(): List<Timezone> = listOf(
            Timezone(UtcOffset(false, 5, 0), "America/New_York"),
            Timezone(UtcOffset(true, 5, 30))
    )

    private fun getXmls(): List<Xml> = listOf(
            Xml("<b>Some xml</b>"),
            Xml("<p>Some <b>larger</b> xml</p>"),
    )

    private fun getAndroidExampleVCard(): VCard =
            """
            BEGIN:VCARD
            VERSION:2.1
            N:Doe;John;Middle
            X-PHONETIC-FIRST-NAME:PhoneFirst
            X-PHONETIC-MIDDLE-NAME:PhoneMiddle
            X-PHONETIC-LAST-NAME:PhoneLast
            X-ANDROID-CUSTOM:vnd.android.cursor.item/relation;SpouseText;14;;;;;;;;;;
             ;;;

            TEL;HOME:078-464-6564
            EMAIL:mail@example.com
            EMAIL;HOME:home@example.com
            EMAIL;WORK:work@example.com
            EMAIL;CELL:mobile@example.com
            ADR;X-CustomAddressType:;;Street, OH, USA;;;;
            ADR;HOME:;;HomeStreet 10;;;;
            ADR;WORK:;;WorkStreet 42
            ADR;HOME:;;OtherStreet 10;;;;
            ORG:CompanyName;DepartmentName
            TITLE:TitleText
            URL:example.com
            PHOTO;JPEG;ENCODING=BASE64:/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBw
             cJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL
             /2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy
             MjIyMjIyMjIyMjIyMjL/wAARCABgAGADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAA
             AECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBka
             EII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZ
             naGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJ
             ytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECA
             wQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRob
             HBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2h
             panN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK
             0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDqOCQe9Hyn6Unfg0pHO
             KgoUjAwP07Ud/QfWkKEHjJPtzQcd80ALx+dJ349expO2e9B96AHZ9qAxxnNNyRgcZpOcc0ASE
             nqRke4pSwBOOB6VCfTdge1AJHc5pgS5IwccUbsEDpUW4k88n3o3D/JouBOsRYlAylvXH9KQox
             X7ueeR3q3uNwgdQGVT1JIz74zR5SQ8Rttz97ZMuG/4CORSDQpMpU8g/hzikB28dz+FXVRFjkK
             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

            NOTE:NotesText
            BDAY:2022-01-26
            X-SIP:00111111111
            END:VCARD
        """.trimIndent().let {
                Ezvcard.parse(it).first()
            }

    @Suppress("DEPRECATION")
    private class TestResources : Resources(null, null, null) {
        override fun getString(id: Int): String {
            return when (id) {
                R.string.phoneTypeCustom -> "Custom"
                R.string.phoneTypeHome -> "Home"
                R.string.phoneTypeMobile -> "Mobile"
                R.string.phoneTypeWork -> "Work"
                R.string.phoneTypePager -> "Pager"
                R.string.phoneTypeOther -> "Other"
                R.string.phoneTypeCar -> "Car"
                R.string.phoneTypeIsdn -> "ISDN"
                R.string.phoneTypeOtherFax -> "Other Fax"

                R.string.eventTypeCustom -> "Custom"
                R.string.eventTypeBirthday -> "Birthday"
                R.string.eventTypeAnniversary -> "Anniversary"
                R.string.eventTypeOther -> "Other"

                R.string.emailTypeHome -> "Home"
                R.string.emailTypeWork -> "Work"
                R.string.emailTypeOther -> "Other"

                R.string.postalTypeCustom -> "Custom"
                R.string.postalTypeHome -> "Home"
                R.string.postalTypeWork -> "Work"
                R.string.postalTypeOther -> "Other"

                R.string.relationTypeCustom -> "Custom"
                R.string.relationTypeChild -> "Child"
                R.string.relationTypeFriend -> "Friend"
                R.string.relationTypeParent -> "Parent"
                R.string.relationTypeSpouse -> "Spouse"

                R.string.contact_property_key -> "Key"
                R.string.header_nickname_entry -> "Nickname"
                R.string.organization_type -> "Organization"

                else -> throw IllegalArgumentException("No such string")
            }
        }
    }

}
