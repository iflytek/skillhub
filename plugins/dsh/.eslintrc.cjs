module.exports = {
  root: true,
  extends: ['../../cli/.eslintrc.cjs'],
  env: { node: true, browser: true },
  ignorePatterns: ['lib/', 'src/host/generated/']
}
