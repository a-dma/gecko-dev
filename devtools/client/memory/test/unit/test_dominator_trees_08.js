/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

// Test that we can change the display with which we describe a dominator tree
// and that the dominator tree is re-fetched.

const {
  snapshotState: states,
  dominatorTreeState,
  viewState,
  dominatorTreeDisplays,
  treeMapState
} = require("devtools/client/memory/constants");
const {
  setDominatorTreeDisplayAndRefresh
} = require("devtools/client/memory/actions/dominator-tree-display");
const {
  changeView,
} = require("devtools/client/memory/actions/view");
const {
  takeSnapshotAndCensus,
  computeAndFetchDominatorTree,
} = require("devtools/client/memory/actions/snapshot");

function run_test() {
  run_next_test();
}

add_task(function *() {
  let front = new StubbedMemoryFront();
  let heapWorker = new HeapAnalysesClient();
  yield front.attach();
  let store = Store();
  let { getState, dispatch } = store;

  dispatch(changeView(viewState.DOMINATOR_TREE));

  dispatch(takeSnapshotAndCensus(front, heapWorker));
  yield waitUntilCensusState(store, s => s.treeMap, [treeMapState.SAVED]);
  ok(!getState().snapshots[0].dominatorTree,
     "There shouldn't be a dominator tree model yet since it is not computed " +
     "until we switch to the dominators view.");

  // Wait for the dominator tree to finish being fetched.
  yield waitUntilState(store, state =>
    state.snapshots[0] &&
    state.snapshots[0].dominatorTree &&
    state.snapshots[0].dominatorTree.state === dominatorTreeState.LOADED);

  ok(getState().dominatorTreeDisplay,
     "We have a default display for describing nodes in a dominator tree");
  equal(getState().dominatorTreeDisplay,
        dominatorTreeDisplays.coarseType,
        "and the default is coarse type");
  equal(getState().dominatorTreeDisplay,
        getState().snapshots[0].dominatorTree.display,
        "and the newly computed dominator tree has that display");

  // Switch to the allocationStack display.
  dispatch(setDominatorTreeDisplayAndRefresh(
    heapWorker,
    dominatorTreeDisplays.allocationStack));

  yield waitUntilState(store, state =>
    state.snapshots[0].dominatorTree.state === dominatorTreeState.FETCHING);
  ok(true,
     "switching display types caused the dominator tree to be fetched " +
     "again.");

  yield waitUntilState(store, state =>
    state.snapshots[0].dominatorTree.state === dominatorTreeState.LOADED);
  equal(getState().snapshots[0].dominatorTree.display,
        dominatorTreeDisplays.allocationStack,
        "The new dominator tree's display is allocationStack");
  equal(getState().dominatorTreeDisplay,
        dominatorTreeDisplays.allocationStack,
        "as is our requested dominator tree display");

  heapWorker.destroy();
  yield front.detach();
});
