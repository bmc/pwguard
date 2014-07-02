#!/bin/bash
#
# This script should be run on the SERVER, not locally.
#
# 1. Create the distribution tarball with "scripts/dist.sh"
# 2. Copy the resulting tarball file to the server, and put it in the
#    top level of the app directory.
# 3. Invoke this script, passing the path to the tarball as the first
#    argument.

PREFIX="pwguard"

RED="\e[00;31m"
GREEN="\e[00;32m"
RESET="\e[00m"
YELLOW="\e[00;33m"

# 1 - color (see above)
# 2 - label
# 3 - message
color()
{
    echo -e "${1}[$2]${RESET} $3"
}

warn()
{
    color "$YELLOW" "WARN" "$*" >&2
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
    exit 1
}

case "$#" in
    1)
        ;;
    *)
        die "Usage: $0 path-to-zip"
        ;;
esac

here=`pwd`

info "Clearing tmp."
mkdir -p tmp
rm -rf tmp/*

# First, unpack the tarball file in a local temporary directory.
info "Unpacking $1."
tarball=$1
tarball_dir=`dirname $tarball`
tarball_file=`basename $tarball`
cd $tarball_dir
tarball_dir=`pwd`
cd $here
tarball_full_path=$tarball_dir/$tarball_file

cd tmp
tar xf $tarball_full_path

# Now, unpack the zip file.

zip="`echo *.zip`"
if [[ "x$zip" = "x*.zip" ]]
then
    die "No zip file in $tarball_full_path"
fi

if [[ ! -f static.tbz ]]
then
    die "Missing expected static.tbz"
fi

info "Unpacking $zip"
unzip -q $zip
dir=`basename $zip .zip`
if [[ ! -d $dir ]]
then
    die "Did not find expected $dir directory."
fi
cd $here

# Now, move the directory to a timestamped installation directory.
timestamped_dir=$PREFIX-`date '+%Y%m%d-%H%M%S.%s'`
info "Moving unpacked distribution to $timestamped_dir"
if [ -d $timestamped_dir ]
then
    die "ERROR: Installation directory $timestamped_dir already exists in $here"
fi

mv "tmp/$dir" $timestamped_dir

if [ ! -d logs ]
then
    info "Creating 'logs' directory in $here"
    mkdir logs
fi

for i in conf logs
do
  info "Pointing $timestamped_dir/$i to $here/$i"
  rm -rf $timestamped_dir/$i
  (cd $timestamped_dir ; ln -s ../$i .)
done

info "Pointing $here/current to $timestamped_dir"
rm -f current
ln -s $timestamped_dir current

#info "Creating 'generated' in $timestamped_dir"
#mkdir -p $timestamped_dir/generated

info "Unpacking static files in $timestamped_dir"
(cd $timestamped_dir; tar xf ../tmp/static.tbz)

info "Making $timestamped_dir/bin files executable."
for i in $timestamped_dir/bin/*
do
  chmod 744 $i
done

if [ -e /etc/init.d/supervisor ]
then
    info "Restarting supervisord, as root. (Enter password, if necessary.)"
    sudo /etc/init.d/supervisor stop
    sleep 3
    # Make sure Java is dead.
    killall -9 java
    sudo /etc/init.d/supervisor start
else
    warn "supervisord doesn't exist. Cannot restart it."
fi

declare -a dirs=($(/bin/ls -1dtr $PREFIX-`date +%Y`*))
let total_dirs=${#dirs[@]}
if ((  $total_dirs > 5 ))
then
    let to_remove=${total_dirs}-5
    info "Removing $to_remove old version(s)."
    let upper_bound=${to_remove}-1
    for i in $(seq 0 $upper_bound)
    do
        dir=${dirs[$i]}
        info "Deleting $dir"
        rm -rf $dir
    done
fi
