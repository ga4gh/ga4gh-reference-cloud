# ga4gh-reference-cloud-helm
Helm chart for the GA4GH Reference Cloud

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine):
  * `docker`
  * `kubectl`
  * `minikube`
  * `helm`
  * `mkcert`
* Ensure your `kubectl` active context is the `minikube` context
* Enable ingress by running the following:
 ```
 minikube addons enable ingress
 minikube addons enable ingress-dns
 ```
* Create an SSL certificate for `refcloud.ga4gh.localhost` and subdomains:
  * **NOTE:** Do this in a secure folder on your machine. DO NOT commit cert/key files to GitHub
  ```
  mkcert -install -cert-file cert.pem -key-file key.pem "*.refcloud.ga4gh.localhost" "refcloud.ga4gh.localhost"
  kubectl create secret tls refcloud-tls --cert=cert.pem --key=key.pem
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
* Run `helm install refcloud charts/app -f charts/app/values.yaml` to install the helm chart in your Minikube cluster
* Run `minikube tunnel` to enable ingress
* You should now be able to access the local service via web browser at `https://refcloud.ga4gh.localhost`
