from fabric.api import run, sudo, env, task, local, put, parallel
from fabric.context_managers import cd
import logging
from fabtools import require

# env.user = 'root'
env.use_ssh_config = True

PACKAGES = ['openjdk-7-jre-headless','python-pip','dstat','htop','supervisor',
            'libjna-java', 'libopts25','ntp', 'python-support',]

folder = "/var/www/star-track"


# @task
# def compile():
#   local("lein uberjar")

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
def install_packages():
    require.deb.package(PACKAGES)

@task
def rpm(license_key):
  sudo("echo deb http://apt.newrelic.com/debian/ newrelic non-free >> /etc/apt/sources.list.d/newrelic.list")
  sudo("wget -O- https://download.newrelic.com/548C16BF.gpg | apt-key add -")
  aptupdate()
  sudo("apt-get install newrelic-sysmond")
  sudo("nrsysmond-config --set license_key={}".format(license_key))
  sudo("/etc/init.d/newrelic-sysmond start")

@task
@parallel
def deploy(restart=True):
  sudo("mkdir -p {}".format(folder))

  jar_file = 'star-tracker.jar'
  new_jar = 'new-' + jar_file
  img = "1x1.png"

  with cd(folder):
    put(img,img,use_sudo=True)
    put("log4j.properties","log4j.properties",use_sudo=True)
    put("target/uberjar/star-tracker.jar", new_jar,use_sudo=True)
    sudo("mv {} {}".format(new_jar, jar_file))

  put('config/supervisor/startrack.conf', '/etc/supervisor/conf.d/startrack.conf',use_sudo=True)
  
  sudo("supervisorctl reread")
  sudo("supervisorctl update")
  if restart:
    sudo("supervisorctl restart prod_startrack")

    # put('target/%s' % jar_file, "new-" + jar_file  )