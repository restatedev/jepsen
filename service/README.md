# restate-ts-template

# Steps

First, get all dependencies and build tools:
```
npm install
```

Next, adjust the 'proto' definitions. Then run:
```
npm run proto
```

Finally, implement your code.
Use `npm run app-dev` to start in dev mode (watch for changes and reload automatically).

To properly build/run the code, do
```
npm run build
npm run app
```


# Contributing to this Template

When contributing changes to this template, please avoid checking in the following files:

  - `package-lock.json`
  - `proto/buf.lock`
  - `src/generated/**`

Those files are created by the install/build process. Some (or all) of those files
would typically be checked in by app developers that build on this template, but
should not be part of the template itself.

We currently avoid adding those files to `.gitignore`, so that we don't exclude them
for developers building on top of this template.

To make development of the template itself easier, you can add a local exclude for those
files by adding the lines below to the file `.git/info/exclude`.
You may need to create the file, if it does not exist, yet.

```
# in the projects created from this template, one would typically check those files in
# but we don't want to check them into the template
package-lock.json
proto/buf.lock
src/generated/proto/dev/restate/ext.ts
src/generated/proto/example.ts
src/generated/proto/google/protobuf/descriptor.ts
```
