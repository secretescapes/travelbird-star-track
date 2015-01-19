from fabric.api import run, sudo, env, task, local, put
from fabric.context_managers import cd
import logging
from fabtools import require

env.user = 'root'
env.use_ssh_config = True


folder = "/var/www/star-track"


@task
def compile():
  local("lein uberjar")

@task
def deploy(restart=True):
  sudo("mkdir -p {}".format(folder))

  jar_file = 'star-tracker.jar'
  new_jar = 'new-' + jar_file
  img = "1x1.png"

  with cd(folder):
    put(img,img )
    put("target/uberjar/star-tracker.jar", new_jar)
    sudo("mv {} {}".format(new_jar, jar_file))

  put('config/supervisor/startrack.conf', '/etc/supervisor/conf.d/startrack.conf')
  
  sudo("supervisorctl reread")

  if restart:

    sudo("supervisorctl restart prod_startracker")

    # put('target/%s' % jar_file, "new-" + jar_file  )