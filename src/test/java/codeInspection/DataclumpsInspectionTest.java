package codeInspection;

import com.github.fiadleh.codesmellsplugin.codesmells.dataclumps.DataclumpsInspection;
import com.github.fiadleh.codesmellsplugin.util.CacheManager;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

/**
 * Class for testing DataclumpsInspection.
 *
 * @author Firas Adleh
 */
public class DataclumpsInspectionTest extends LightJavaCodeInsightFixtureTestCase {

    private static DataclumpsInspection dataclumpsProfile = new DataclumpsInspection();

    /**
     * Defines path to files used for running tests.
     *
     * @return The path from this module's root directory ($MODULE_WORKING_DIR$) to the
     * directory containing files for these tests.
     */
    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }


    /**
     * Checks a given Data Clumps test case file. The test  case can
     *
     * @param filePath              filepath for the test case
     * @param dataclumpsPositive    true if the test case contains an instance of Data Clumps that should be detected
     */
    protected void doTestCase(String filePath, boolean dataclumpsPositive) {
        myFixture.disableInspections();
        // Initialize the test based on the given file path
        myFixture.configureByFile(filePath);
        // activate testing by the inspection
        DataclumpsInspection.activateTesting();
        // Initialize the inspection and get highlighted code
        myFixture.enableInspections(dataclumpsProfile);
        List<HighlightInfo> highlightInfos = myFixture.doHighlighting();


        // check highlighting
        if (dataclumpsPositive) {
            System.out.println(highlightInfos);
            assertTrue(hasDataclumps(highlightInfos));
        } else {
            assertFalse(hasDataclumps(highlightInfos));
        }
        // Get the quick fix action
        List<IntentionAction> actionList = myFixture.filterAvailableIntentions(DataclumpsInspection.QUICK_FIX_NAME);
        System.out.println("actionList: " + actionList.size());
        // check reporting
        if (dataclumpsPositive) {
            assertNotEmpty(actionList);
        } else {
            assertEmpty(actionList);
        }
        // reset cache to be used in other tests
        CacheManager.resetIsCacheReady();
    }

    public boolean hasDataclumps(List<HighlightInfo> highlightInfos) {
        for (HighlightInfo highlightInfo : highlightInfos) {
            if (highlightInfo.getInspectionToolId() != null && highlightInfo.getInspectionToolId().equals("Dataclumps")) {
                return true;
            }
        }
        return false;
    }

    public void testPolymorphism() {
        doTestCase("dataclumps/Polymorphism.java", true);
    }

    public void testInnerClass() {
        doTestCase("dataclumps/InnerClass.java", true);
    }

    public void testSimpleFields() {
        doTestCase("dataclumps/SimpleFields.java", true);
    }

    public void testSimpleParameters() {
        doTestCase("dataclumps/SimpleParameters.java", true);
    }

    public void testInterfaceNegative() {
        doTestCase("dataclumps/InterfaceNegative.java", false);
    }

    public void testInterfacePositive() {
        doTestCase("dataclumps/InterfacePositive.java", true);
    }

    public void testDistinctNamesTypes() {
        doTestCase("dataclumps/DistinctNamesTypes.java", false);
    }

    public void testSameClass() {
        doTestCase("dataclumps/SameClass.java", true);
    }

    public void testAnonymous() {
        doTestCase("dataclumps/AnonymousClass.java", true);
    }


}
