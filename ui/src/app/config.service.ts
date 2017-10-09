import { Http } from '@angular/http';
import { Injectable } from '@angular/core';

// See https://angular.io/tutorial/toh-pt6
import 'rxjs/add/operator/toPromise';

@Injectable()
export class ConfigService {
  private config = null;

  constructor(private http: Http) {
  }

  getConfig(): Promise<any> {
    if (this.config != null)
      return Promise.resolve(this.config);

    return this.http
      .get("config")
      .toPromise()
      .then(response => {
        this.config = response.json();
        return this.config;
      })
      .catch(this.handleError);
  }

  private handleError(error: any): Promise<any> {
    console.error('An error occurred while fetching the config:', error);
    return Promise.reject(error.message || error)
  }
}

