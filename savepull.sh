#!/bin/bash
branch=$1
git stash
git checkout -b ${branch}
git stash pop
git add .
git commit -m "Merge in changes needed for ${branch}"
git push --set-upstream origin ${branch} 

