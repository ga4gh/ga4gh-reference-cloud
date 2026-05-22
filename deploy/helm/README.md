# ga4gh-reference-cloud-helm
Helm chart for the GA4GH Reference Cloud

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine):
  * `docker`
  * `kubectl`
  * `minikube`
  * `helm`
* Ensure your `kubectl` active context is the `minikube` context
* Enable ingress by running the following:
 ```
 minikube addons enable ingress
 minikube addons enable ingress-dns
 ```
* edit your `/etc/hosts` file with the following line(s) to point the ingress URLs to localhost:
  ```
  127.0.0.1	refcloud.ga4gh.local
  ```
* Create a kubernetes secret to enable minikube to pull images from private ECR repository, e.g.
  ```
  TOKEN=$(aws ecr get-login-password --region <your-region>)
  kubectl create secret docker-registry ecr-registry-key \
    --docker-server=<aws_account_id>.dkr.ecr.<region>.amazonaws.com \
    --docker-username=AWS \
    --docker-password=$TOKEN \
    --namespace=<your-namespace>
  ```
* Run `helm install refcloud charts/app` to install the helm chart in your Minikube cluster
* Run `minikube tunnel` to enable ingress
* You should now be able to access the local service via web browser at `http://refcloud.ga4gh.local`
