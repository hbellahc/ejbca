/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.webtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.WebTestBase;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.helper.AuditLogHelper;
import org.ejbca.helper.EndEntityProfileHelper;
import org.ejbca.helper.WebTestHelper;
import org.ejbca.utils.WebTestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Test to verify that End Entity Profile management operations work as expected.
 * 
 * @version $Id$
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EcaQa99_EEPManagement extends WebTestBase {

    private static final AuthenticationToken admin = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("UserDataTest"));

    private static WebDriver webDriver;
    private static EndEntityProfileSessionRemote endEntityProfileSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityProfileSessionRemote.class);

    private static final String deleteAlert = "Are you sure you want to delete this?";
    private static final String eepName = "ECAQA10-EndEntityProfile";
    private static final String eepNameClone = "TestEndEntityProfileFromTemplate";
    private static final String eepRename = "MyEndEntityProfile";

    @BeforeClass
    public static void init() {
        setUp(true, null);
        webDriver = getWebDriver();
    }

    @AfterClass
    public static void exit() throws AuthorizationDeniedException {
        endEntityProfileSession.removeEndEntityProfile(admin, eepName);
        endEntityProfileSession.removeEndEntityProfile(admin, eepNameClone);
        endEntityProfileSession.removeEndEntityProfile(admin, eepRename);
        webDriver.quit();
    }

    @Test
    public void testA_addEEP() {
        AuditLogHelper.resetFilterTime();

        // Create EEP and enter edit mode
        EndEntityProfileHelper.goTo(webDriver, getAdminWebUrl());
        EndEntityProfileHelper.add(webDriver, eepName, true);
        EndEntityProfileHelper.edit(webDriver, eepName);

        // Edit EEP and save
        webDriver.findElement(By.id("checkboxautogeneratedusername")).click();
        EndEntityProfileHelper.save(webDriver, true);

        // Assert that the EEP exists and then enter edit mode
        EndEntityProfileHelper.assertExists(webDriver, eepName);
        EndEntityProfileHelper.edit(webDriver, eepName);

        WebElement autoGenUsernameButton = webDriver.findElement(By.id("checkboxautogeneratedusername"));
        assertNotNull("'Auto-generated username' was not enabled after saving EEP", autoGenUsernameButton.getAttribute("checked"));

        // Click 'Back to End Entity Profiles' link and assert EEP still exists
        webDriver.findElement(By.xpath("//td/a[contains(@href,'editendentityprofiles.jsp')]")).click();
        assertEquals("Clicking 'Back to End Entity Profiles' link did not redirect to expected page", WebTestUtils.getUrlIgnoreDomain(webDriver.getCurrentUrl()),
                "/ejbca/adminweb/ra/editendentityprofiles/editendentityprofiles.jsp");
        EndEntityProfileHelper.assertExists(webDriver, eepName);

        // Verify Audit Log
        AuditLogHelper.goTo(webDriver, getAdminWebUrl());
        AuditLogHelper.assertEntry(webDriver, "End Entity Profile Add", "Success", null,
                Arrays.asList("End entity profile " + eepName + " added."));
        AuditLogHelper.assertEntry(webDriver, "End Entity Profile Edit", "Success", null,
                Arrays.asList("End entity profile " + eepName + " edited."));
    }

    @Test
    public void testB_addEEPClone() {
        AuditLogHelper.resetFilterTime();
        EndEntityProfileHelper.goTo(webDriver, getAdminWebUrl());

        // Clone EEP
        EndEntityProfileHelper.clone(webDriver, eepName, eepNameClone);
        EndEntityProfileHelper.assertExists(webDriver, eepNameClone);

        // Verify Audit Log
        AuditLogHelper.goTo(webDriver, getAdminWebUrl());
        AuditLogHelper.assertEntry(webDriver, "End Entity Profile Add", "Success", null,
                Arrays.asList("Added new end entity profile " + eepNameClone + " using profile " + eepName + " as template."));

        // Rename EEP
        AuditLogHelper.resetFilterTime();
        EndEntityProfileHelper.goTo(webDriver, getAdminWebUrl());
        EndEntityProfileHelper.rename(webDriver, eepName, eepRename);

        // Verify Audit Log
        AuditLogHelper.goTo(webDriver, getAdminWebUrl());
        AuditLogHelper.assertEntry(webDriver, "End Entity Profile Rename", "Success", null,
                Arrays.asList("End entity profile " + eepName + " renamed to " + eepRename + "."));
    }

    @Test
    public void testC_removeEEP() {
        AuditLogHelper.resetFilterTime();

        // Try to remove EEP
        EndEntityProfileHelper.goTo(webDriver, getAdminWebUrl());
        EndEntityProfileHelper.delete(webDriver, eepRename);

        // Dismiss alert, assert that EEP still exists
        WebTestHelper.assertAlert(webDriver, deleteAlert, false);
        EndEntityProfileHelper.assertExists(webDriver, eepRename);

        // Actually delete EEP
        EndEntityProfileHelper.delete(webDriver, eepRename);
        WebTestHelper.assertAlert(webDriver, deleteAlert, true);

        // Verify Audit Log
        AuditLogHelper.goTo(webDriver, getAdminWebUrl());
        AuditLogHelper.assertEntry(webDriver, "End Entity Profile Remove", "Success", null,
                Arrays.asList("End entity profile " + eepRename + " removed."));
    }
}