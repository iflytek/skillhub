import { defineBuildConfig } from "unbuild";

export default defineBuildConfig({
  entries: ["src/cli"],
  outDir: "dist",
  clean: true,
  rollup: {
    emitCJS: false,
  },
});
