package com.automation.studio;

import com.automation.studio.actions.ActionCatalog;
import com.automation.studio.actions.ActionDefinition;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

/** Fails when an action lacks a code generator, an executor, a category or a unique id. */
public class ActionCatalogCoverageTest {
    private final ActionCatalog catalog = new ActionCatalog();

    @Test
    public void everyActionIsComplete() {
        Set<String> ids = new HashSet<>();
        for (ActionDefinition d : catalog.all()) {
            Assert.assertTrue(ids.add(d.id), "duplicate id " + d.id);
            Assert.assertNotNull(d.codeGen, d.id + " has no code template");
            Assert.assertNotNull(d.executor, d.id + " has no executor");
            Assert.assertFalse(d.category.isBlank(), d.id + " has no category");
            Assert.assertFalse(d.displayName.isBlank(), d.id + " has no display name");
        }
    }

    @Test
    public void coversTheCoreCategoriesAndActions() {
        Assert.assertTrue(catalog.all().size() >= 100, "catalog unexpectedly small: " + catalog.all().size());
        for (String id : new String[]{"NAVIGATE", "CLICK", "DBLCLICK", "FILL", "CHECK", "UNCHECK", "SELECT_OPTION", "HOVER", "PRESS",
                "SET_INPUT_FILES", "ASSERT_IS_VISIBLE", "ASSERT_HAS_TEXT", "ASSERT_PAGE_HAS_URL", "ASSERT_PAGE_HAS_TITLE",
                "MOCK_ROUTE", "API_REQUEST", "DIALOG_ACCEPT", "SWITCH_TAB", "DOWNLOAD_FILE", "SET_GEOLOCATION", "TEXT_CONTENT"})
            Assert.assertNotNull(catalog.get(id), "missing action " + id);
        for (String cat : new String[]{"Assertions", "Interaction", "Input", "Waits", "Navigation", "Data Extraction", "Network", "API", "Dialogs", "Files", "Context", "Emulation"})
            Assert.assertTrue(catalog.categories().contains(cat), "missing category " + cat);
    }
}
