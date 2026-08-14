# CLAUDE.md

## Landmines & Non-Obvious Requirements

### Maven Settings
Builds require `.mvn/settings.xml` (JFrog, GitHub Packages, Sonatype credentials via env vars). CI passes it with `-s .mvn/settings.xml`. `.mvn/maven.config` auto-activates the Polarion version profile.

### Polarion Dependencies
You must extract dependencies from the Polarion installer using [polarion-artifacts-deployer](https://github.com/SchweizerischeBundesbahnen/polarion-artifacts-deployer) before the Maven build will work.

### Local Polarion Installation
Requires `POLARION_HOME` environment variable. Use the `install-to-local-polarion` Maven profile:
```bash
mvn clean install -P install-to-local-polarion
```

### Remote Debugging
Add to Polarion's `config.sh`:
```bash
JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

### Logging
Polarion logs: `<POLARION_HOME>/polarion/logs/main/*.log`

### Testing `ui/` against a local react-sbb-polarion build
Never point the RSP dependency at the sibling checkout (`file:../../react-sbb-polarion`). Host `npm test` still passes, so the break is invisible until `npm run test:docker` fails with `npm ci` `EUSAGE`: `ui/scripts/docker-test.mjs` mounts `ui/` alone at `/work`, so the symlink target sits outside the container. A linked package also contributes no transitive dependencies, so `refractor` silently goes missing from the tree.

Pack a tarball into `ui/` instead, which resolves inside the mount. From `ui/`:
```bash
(cd ../../react-sbb-polarion && npm pack --pack-destination ../ch.sbb.polarion.extension.xml-repair/ui)
# then set in ui/package.json:
#   "@grigoriev/react-sbb-polarion": "file:grigoriev-react-sbb-polarion-<version>.tgz"
npm install
```
After every RSP change, repack and then force the lockfile entry to be re-derived:
```bash
node -e "const f='package-lock.json',fs=require('fs'),l=JSON.parse(fs.readFileSync(f,'utf8'));delete l.packages['node_modules/@grigoriev/react-sbb-polarion'];fs.writeFileSync(f,JSON.stringify(l,null,2)+'\n')"
rm -rf node_modules/@grigoriev && npm install
```
A plain `npm install` is not enough: the dependency spec string is unchanged, so npm installs the new tarball contents but leaves the **old** integrity hash in the lockfile. The host build then passes while `npm ci` in the container dies with `EINTEGRITY`, naming a hash that matches nothing on disk. The file name carries the version, so a version bump also needs the `package.json` line updated and the old tarball deleted.

Verify with `npm run test:docker`, not bare `npm test`. Restore the published version range and the tarball-free lockfile before committing.

## Branch & Commit Conventions

- Conventional commits enforced by commitizen (pre-commit hook)
- Feature branches: `feature/<name>`
- Bug fixes: `fix/<name>`
- LTS branches: `release-v*` (e.g., `release-v6`)
