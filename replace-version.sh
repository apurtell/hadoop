if [ $# = 2 ]; then
  find . -name pom.xml -exec $0 $1 $2 \{} \;
elif [ $# = 3 ]; then
  sed s/$1/$2/ $3 > $3.tmp
  mv $3.tmp $3
  git add $3
else
  echo "Usage: $0 oldversion newversion [file] (default all pom.xml)"
fi
