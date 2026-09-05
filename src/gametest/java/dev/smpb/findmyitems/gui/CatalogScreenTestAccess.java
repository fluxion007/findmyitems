package dev.smpb.findmyitems.gui;

import java.util.OptionalInt;

/** Typed client-game-test bridge for CatalogScreen's package-private probes. */
public final class CatalogScreenTestAccess {
    private CatalogScreenTestAccess() {
    }

    public record BrowseState(int rowCount, int planRequests, boolean rootRows,
                              boolean selected, boolean hovered) {
    }

    public record GenerationValues(long searchGeneration, long planGeneration, long appliedPlanGeneration) {
    }

    public record SelectionState(int planRequests, boolean selected, boolean hovered, boolean stableIdentity,
                                 GenerationValues generations) {
    }

    public static int rowCount(CatalogScreen screen) {
        return screen.currentRows().size();
    }

    public static String statusText(CatalogScreen screen) {
        return screen.statusText();
    }

    public static boolean craftingActionsVisible(CatalogScreen screen) {
        return screen.craftingActionsVisible();
    }

    public static boolean craftingActionsActive(CatalogScreen screen) {
        return screen.craftingActionsActive();
    }

    public static void selectOutput(CatalogScreen screen, dev.smpb.findmyitems.model.StackKey key) {
        screen.selectOutput(key);
    }

    public static BrowseState browseState(CatalogScreen screen) {
        return new BrowseState(screen.currentRows().size(), screen.planRequestCount(),
                screen.currentRows().stream().allMatch(row -> row.outputIdentity() != null),
                screen.selectedIdentity() != null, screen.hoveredIdentity() != null);
    }

    public static int visibleRowCount(CatalogScreen screen) {
        return screen.visibleRowCount();
    }

    public static int renderedRowCount(CatalogScreen screen) {
        return screen.renderedRowCount();
    }

    public static double scrollAmount(CatalogScreen screen) {
        return screen.scrollAmount();
    }

    public static OptionalInt hitTestRow(CatalogScreen screen, double x, double y) {
        return screen.hitTestRow(x, y);
    }

    public static double[] firstVisibleRowCenter(CatalogScreen screen) {
        var bounds = screen.firstVisibleRowBounds();
        return new double[] {bounds.left() + bounds.width() / 2.0, bounds.top() + bounds.height() / 2.0};
    }

    public static double[] firstVisibleRowTakeCenter(CatalogScreen screen) {
        var bounds = screen.firstVisibleRowBounds();
        return new double[] {bounds.left() + bounds.width() - 10, bounds.top() + bounds.height() / 2.0};
    }

    public static double[] lastVisibleRowBottomCenter(CatalogScreen screen) {
        var bounds = screen.lastVisibleRowBounds();
        return new double[] {bounds.left() + bounds.width() / 2.0, bounds.top() + bounds.height() - 0.1};
    }

    public static double[] firstVisibleCellCenter(CatalogScreen screen) {
        var bounds = screen.firstVisibleRowBounds();
        return new double[] {bounds.left() + 9, bounds.top() + bounds.height() / 2.0};
    }

    public static String rowKind(CatalogScreen screen) {
        var rows = screen.currentRows();
        return rows.isEmpty() ? "empty" : rows.getFirst().getClass().getSimpleName();
    }

    public static java.util.List<int[]> widgetBounds(CatalogScreen screen) {
        return screen.interactiveBounds();
    }

    public static SelectionState selectionState(CatalogScreen screen) {
        var generation = screen.generationState();
        var selected = screen.selectedIdentity();
        var hovered = screen.hoveredIdentity();
        return new SelectionState(screen.planRequestCount(), selected != null,
                hovered != null, selected != null && selected.equals(hovered),
                new GenerationValues(generation.searchGeneration(), generation.planGeneration(),
                        generation.appliedPlanGeneration()));
    }

    public static boolean hasHoveredIdentity(CatalogScreen screen) {
        return screen.hoveredIdentity() != null;
    }

    public static boolean locateVisible(long indexedCount, boolean hasPosition) {
        return CatalogScreen.locateVisible(indexedCount, hasPosition);
    }

    public static String automaticStatusKey(long missing, long indexed, boolean reachableStorage,
                                             boolean reachableCraftingTable) {
        return CatalogScreen.automaticStatusKey(missing, indexed, reachableStorage, reachableCraftingTable);
    }

    public static String viewName(CatalogScreen screen) {
        return screen.currentViewName();
    }
}
