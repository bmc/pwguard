#!/bin/bash
#
# Call this script to prepare the code for deployment.

# Location of additional static files to be deployed (that aren't part of
# the asset pipeline). Must be a relative path!
ADDITIONAL_STATIC=static

# Additional configs not generally picked up by Play's "dist" task. Must be
# relative paths!
EXTRA_CONFIGS="conf/play.plugins"

RED="\e[00;31m"
GREEN="\e[00;32m"
YELLOW="\e[00;33m"
RESET="\e[00m"

# 1 - color (see above)
# 2 - label
# 3 - message
color()
{
  echo -e "${1}[$2]${RESET} $3"
}

warn()
{
  color "$YELLOW" "WARN" "$*"
}

error()
{
  color "$RED" "ERROR" "$*" >&2
}

info()
{
  color "$GREEN" "INFO" "$*"
}

die()
{
  error "$*"
  rm -f "$(pwd)/conf/version.conf"
  exit 1
}

mkdir -p dist

info "Clearing dist directory."
rm -rf dist/*

info "Creating version.conf"
version="$(date '+%Y%m%d.%H%M%S') ($(git rev-parse --short master))"
echo "version: $version" >$(pwd)/conf/version.conf

info "Generating Play application distribution."
play dist || die "Failed."
cp target/universal/*.zip dist
rm $(pwd)/conf/version.conf

tmp=/tmp/phmdist$$
mkdir $tmp || die "Can't create $tmp directory"

static=$(pwd)/dist/static.tbz

if [ -d $ADDITIONAL_STATIC ]
then
  info "Gathering miscellaneous static files."
  mkdir -p $tmp/$ADDITIONAL_STATIC || die "Can't create $tmp/ADDITIONAL_STATIC"
  cp -rv $ADDITIONAL_STATIC $tmp
  (cd $tmp; tar cjf $static .) || die "Failed to create $static"
  rm -rf $tmp
else
  warn "$ADDITIONAL_STATIC does not exist. Skipping it."
fi

if [ -n "$EXTRA_CONFIGS" ]
then
  info "Gathering extra config files."
  for i in $EXTRA_CONFIGS
  do
    target=$tmp/$(dirname $i)
    mkdir -p $target || die "Can't create $target"
    cp $i $target
  done
fi

info "Packaging everything into dist/deploy.tbz"
cd dist
tar cjf deploy.tbz static.tbz *.zip || die "Failed"

info "Done! Instructions follow."

cat <<EOF
1. Copy dist/deploy.tbz and scripts/deploy.sh to the server.
2. If you haven't already created the initial deployment infrastructure
   (as outlined in README.md), do so.
3. "su" to the "phmh" user.
4. "cd" to "app/phmh-server".
5. Run deploy.sh, pointing it to the deploy.tbz file.
EOF


