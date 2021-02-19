def call(body) {
  // evaluate the body block, and collect configuration into the object
  def pipelineParams= [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = pipelineParams
  body()

  pipeline {
      // Параметр из Folder Properties p_folder_git_rev 'Имя ветки или тега с которого собираем'

      // Параметр из Folder Properties p_folder_namespace Имя окружения, куда катим

      // Параметр из Folder Properties p_kubeconfig Переменная с путём к конфигу kubernetes для  helm и kubectl

      // Параметр из Folder Properties p_registry Адрес регистри в s3

      // Параметр из Folder Properties p_git_creds Credential c доступом к гит-репозиторию проекта

      // Адрес git repo
      // p_git_repo

      // Тип метки для получения replicaset
      // p_replicaset_label

      // Имя проекта = имя helm чарта
      // p_project_name

    // Агентов указываем в каждой конкретной стадии
    agent none
    stages{
      stage('check parameters') {
        agent {
          label 'backend-agent'
        }
        steps {
          script {
            withFolderProperties{
                // Получаем переменную p_folder_git_rev из Folder Properties
                def j_folder_git_rev = p_folder_git_rev
                f_git_rev = j_folder_git_rev
                println "f_git_rev: ${f_git_rev}"

                // Получаем переменную p_folder_namespace из Folder Properties
                def j_folder_namespace = p_folder_namespace
                f_namespace = j_folder_namespace
                println "f_namespace: ${f_namespace}"

                // Получаем переменную p_folder_kubeconfig из Folder Properties
                def j_folder_kubeconfig = p_folder_kubeconfig
                f_kubeconfig = j_folder_kubeconfig
                println "f_kubeconfig: ${f_kubeconfig}"

                // Получаем переменную p_folder_registry из Folder Properties
                def j_folder_registry = p_folder_registry
                f_registry = j_folder_registry
                println "f_registry: ${f_registry}"

                // Получаем переменную p_git_creds из Folder Properties
                def j_git_creds = p_git_creds
                f_git_creds = j_git_creds
                println "f_git_creds: ${f_git_creds}"
                // sh "env|sort"
             }
          }
        }
      }
      stage('checkout') {
        agent {
          label 'backend-agent'
        }
        steps {
          // Клонируем, ветка master
          checkout([$class: 'GitSCM', branches: [[name: "master"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'CloneOption', timeout: 220]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${f_git_creds}", url: "${pipelineParams.p_git_repo}"]]])
          script {
            // Строим скписок тегов
            j_git_tags = sh(returnStdout: true, script: "git tag").trim()
            j_git_tags_array = j_git_tags.split()
            j_git_tags_array.each {
              println "Tag: ${it}"
            }
            // Строим список веток
            j_git_branches = sh(returnStdout: true, script: "git branch -a").trim()
            j_git_branches_array = j_git_branches.split('\n')
            // Убираем пробелы
            for (i in 0..<j_git_branches_array.size()) {
              j_git_branches_array[i] = j_git_branches_array[i].trim()
            }
            j_git_branches_array_filtered = j_git_branches_array.findAll{ it.startsWith("remotes/origin") }
            // Убираем remotes/origin/
            for (i in 0..<j_git_branches_array_filtered.size()) {
              j_git_branches_array_filtered[i] = j_git_branches_array_filtered[i].substring(15)
            }
            j_git_branches_array_filtered.each {
              println "Branch: ${it}"
            }
            // Теги и ветки
            j_git_branches_and_arrays = []
            j_git_branches_and_arrays.addAll(j_git_branches_array_filtered)
            j_git_branches_and_arrays.addAll(j_git_tags_array)
            j_git_branches_and_arrays.each {
              println "Branch Or Tag: ${it}"
            }
            // Есть ли ветка или тег с именем f_git_rev
            if (j_git_branches_and_arrays.contains(f_git_rev)) {
              println( "Branch or tag '${f_git_rev}' exists. Will use it" );
              j_git_rev = f_git_rev
            } else {
              println( "Branch or tag '${f_git_rev}' does not exists. Will use 'master' branch" );
              j_git_rev = "master"
            }

            // Собираем только или из ветки указанной в параметрах Folder'а, если её нет, то из мастера

            // Проверям, не через гитлаб хук ли вызвали
            // sh "env|sort"
            def j_gitlabBranch = sh(script: 'echo ${gitlabBranch}', returnStdout: true).trim()

            println "j_gitlabBranch: ${j_gitlabBranch}"
            // Начальное значение для переменной выхода
            j_aborted = false
            if (j_gitlabBranch != "") {
              println "Looks like gitlab triggered job"
              if (j_gitlabBranch != j_git_rev) {
                println "The gitlab triggered branch '${j_gitlabBranch}' differs from choosed branch '${j_git_rev}'. Abort job"
                j_aborted = true
               }
            }
            // Выходить надо из каждого step
            if (j_aborted) {
              println "Abort job"
              currentBuild.result = 'ABORTED'
              j_aborted = true
              return
            }

            println "Continue job using branch or tag: ${j_git_rev}"

            //slug
            j_git_rev_slug = j_git_rev.toLowerCase()
            j_git_rev_slug = j_git_rev_slug.replaceAll(/[^a-z0-9]+/,"-")
            if (j_git_rev_slug.length() > 20) {
               j_git_rev_slug15 = j_git_rev_slug.substring(0,14)
               j_git_rev_slug16 = j_git_rev_slug.substring(15)
               if (j_git_rev_slug16.length() < 4) {
                 j_git_rev_slug16_length = j_git_rev_slug16.length()
                 j_git_rev_slug16 = j_git_rev_slug16.padRight(4 - j_git_rev_slug16_length, j_git_rev_slug15[0])
               }
               j_git_rev_slug16 = j_git_rev_slug16.reverse().substring(0,4)
               j_git_rev_slug = j_git_rev_slug15 + "-" + j_git_rev_slug16
               j_git_rev_slug = j_git_rev_slug.replaceAll(/[-]+$/, "")
            }
            println "j_git_rev_slug ${j_git_rev_slug}"

            // sh "env|sort"
          }

          // checkout без указания ветки, так как при вызове через hook gitlab'ом ветка почему-то берётся из параметров переданных хуком
          checkout([$class: 'GitSCM', doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'CloneOption', timeout: 220]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${f_git_creds}", url: "${pipelineParams.p_git_repo}"]]])
          // Сами переключаемся на нужную ветку

          // Выходить надо из каждого step, и , видимо, скрипта
          script{
            if (j_aborted) {
              println "Abort job"
              currentBuild.result = 'ABORTED'
              j_aborted = true
              return
            }
            git branch: "${j_git_rev}", credentialsId: "${f_git_creds}", url: "${pipelineParams.p_git_repo}"
            sh "git status"
          }
        }
      }
      stage('build and push') {
        agent {
          label 'backend-agent'
        }
        steps{
          // Выходить надо из каждого step
          script {
            if (j_aborted) {
              println "Abort job"
              currentBuild.result = 'ABORTED'
              j_aborted = true
              return
            }

            // Берём id последнего коммита
            j_git_commit = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
            // https://wiki.jenkins.io/display/JENKINS/Amazon+ECR
            // ecr - id credentials c подключением к ecr aws
            docker.withRegistry("https://${f_registry}", "ecr:eu-central-1:ecr") {
              // Имя образа = Имя_репы:Тег_или_ветка-Коммит
              // Имя репозитория совпадает с именем джоба
              def image = docker.build("${pipelineParams.p_project_name}:${j_git_rev_slug}-${j_git_commit}")
              image.push()
            }
          }
        }
      }
      stage('helminstall') {
        agent {
          label 'kubernetes-agent'
        }
        steps{
          // Выходить надо из каждого step
          script {
            if (j_aborted) {
              println "Abort job"
              currentBuild.result = 'ABORTED'
              j_aborted = true
              return
            }

            // Репозиторий нужен, так как там helm чарт
            checkout([$class: 'GitSCM', branches: [[name: "${j_git_rev}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout'], [$class: 'CloneOption', timeout: 220]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${f_git_creds}", url: "${pipelineParams.p_git_repo}"]]])
            git branch: "${j_git_rev}", credentialsId: "${f_git_creds}", url: "${pipelineParams.p_git_repo}"
            // helm chart лежит в директории .helm, перемещаем в "нормальную директорию"
            // dapp=no - поддержка этого должна быть в репозитории, параметры image определяются в зависимости от выката dapp'ом или helm'ом
            sh """echo \"${f_registry}/${pipelineParams.p_project_name}:${j_git_rev_slug}-${j_git_commit}\"
                  mv .helm ${pipelineParams.p_project_name}
                  KUBECONFIG=${f_kubeconfig} \\
                  helm upgrade -i --namespace ${f_namespace} \\
                          --set \"global.env=${f_namespace}\" \\
                          --set \"global.registry_image=\"${f_registry}/${pipelineParams.p_project_name}:${j_git_rev_slug}-${j_git_commit}\"\" \\
                          --set \"dapp=no\" \\
                          ${pipelineParams.p_project_name}-${f_namespace} \\
                          ./${pipelineParams.p_project_name}
            """
          }
        }
      }
      stage('get replicaset') {
        agent {
          label 'kubernetes-agent'
        }
        steps{
          // Выходить надо из каждого step
          script {
            if (j_aborted) {
              println "Abort job"
              currentBuild.result = 'ABORTED'
              j_aborted = true
              return
            }
            // Получаем репликасеты и берём последний
            def repsetoutput = sh(script: "KUBECONFIG=${f_kubeconfig} kubectl -n ${f_namespace} get replicaset --no-headers=true -l ${pipelineParams.p_replicaset_label}=${pipelineParams.p_project_name} --sort-by=.metadata.creationTimestamp | tail -n 1", returnStdout: true)
            repsetarray = repsetoutput.split()
            println repsetarray[0] //NAME
            println repsetarray[1] //DESIRED
            println repsetarray[2] //CURRENT
            println repsetarray[3] //READY
            println repsetarray[4] //AGE
            replicaset = repsetarray[0]
            desired = repsetarray[1].toInteger()
            current = repsetarray[2].toInteger()
            ready = repsetarray[3].toInteger()
            // Ждём пока current догонит desired
            while(current < desired) {
              println "current < desired"
              sleep(1)
              repsetoutput = sh(script: "KUBECONFIG=${f_kubeconfig} kubectl -n ${f_namespace} get replicaset --no-headers=true ${replicaset}", returnStdout: true)
              repsetarray = repsetoutput.split()
              current = repsetarray[2].toInteger()
              ready = repsetarray[3].toInteger()
              println current
            }
            // Ждём пока ready догонит desired
            while(ready < desired) {
              println "ready < desired"
              sleep(1)
              repsetoutput = sh(script: "KUBECONFIG=${f_kubeconfig} kubectl -n ${f_namespace} get replicaset --no-headers=true ${replicaset}", returnStdout: true)
              repsetarray = repsetoutput.split()
              ready = repsetarray[3].toInteger()
              println ready
            }
          }
        }
      }
    }
  }
}
