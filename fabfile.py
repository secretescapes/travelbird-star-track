from fabric.api import run, sudo, env, task, local, put, parallel
from fabric.context_managers import cd
import logging
from fabtools import require

# env.user = 'root'
env.use_ssh_config = True

PACKAGES = ['openjdk-7-jre-headless','python-pip','dstat','htop','supervisor',
            'libjna-java', 'libopts25','ntp', 'python-support',]

folder = "/var/www/star-track"


@task
@parallel
def runx(command):
    run("{}".format(command))

@task
def nginx():
  sudo("add-apt-repository ppa:nginx/stable")
  sudo("apt-get update")
  sudo("apt-get install nginx -y")


@task
def setup_nginx():
  put("config/nginx/nginx.conf", "/etc/nginx/nginx.conf", use_sudo=True)
  put("config/nginx/star-track.conf","/etc/nginx/sites-enabled/star-track.conf",use_sudo=True)

  try:
    sudo("rm /etc/nginx/sites-enabled/default")
  except:
    pass

  sudo("service nginx reload")


@task
def setup_supervisor():

  put("config/supervisor/startrack.conf","/etc/supervisor/conf.d/startrack.conf",use_sudo=True)
  
  sudo("supervisorctl reread")


def aptupdate():
    sudo("apt-get update")

@task
def install_packages():
    aptupdate()
    require.deb.package(PACKAGES)

def newrelic_setup():
    pass

@task
def rpm(license_key):
  sudo("echo deb http://apt.newrelic.com/debian/ newrelic non-free >> /etc/apt/sources.list.d/newrelic.list")
  sudo("wget -O- https://download.newrelic.com/548C16BF.gpg | apt-key add -")
  aptupdate()
  sudo("apt-get install newrelic-sysmond")
  sudo("nrsysmond-config --set license_key={}".format(license_key))
  sudo("/etc/init.d/newrelic-sysmond start")

@task
def apm():
  app_folder()
  with cd(folder):
    sudo("wget https://s3-us-west-1.amazonaws.com/star-track.public/newrelic.jar")
    sudo("wget https://s3-us-west-1.amazonaws.com/star-track.public/newrelic.yml")

@task
def app_folder():
  sudo("mkdir -p {}".format(folder))

@task
@parallel
def deploy(restart=True):
  app_folder()

  jar_file = 'star-tracker.jar'
  new_jar = 'new-' + jar_file
  img = "1x1.png"

  with cd(folder):
    put(img,img,use_sudo=True)
    put("log4j.properties","log4j.properties",use_sudo=True)
    put("target/uberjar/star-tracker.jar", new_jar, use_sudo=True)
    sudo("mv {} {}".format(new_jar, jar_file))

  # put('config/supervisor/startrack.conf', '/etc/supervisor/conf.d/startrack.conf',use_sudo=True)
  
  # sudo("supervisorctl reread")
  # sudo("supervisorctl update")
  # if restart:
    # sudo("supervisorctl restart prod_startrack")

    # put('target/%s' % jar_file, "new-" + jar_file  )