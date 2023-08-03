import { configureStore } from "@reduxjs/toolkit";
import signupSlice from "./signup";
import userdataSlice from "./userdata.js";
import openviduSlice from "./openviduStore.js";
import matchingSlice from "./matchingStore.js"

export default configureStore({
  reducer: {
    signupReducer: signupSlice.reducer,
    userdataReducer: userdataSlice.reducer,
    openviduReducer: openviduSlice.reducer,
    matchingReducer: matchingSlice.reducer,
  },
});