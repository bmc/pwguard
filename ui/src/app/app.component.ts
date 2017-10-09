import { Component } from '@angular/core';
import { OnInit } from '@angular/core';
import { ConfigService } from "./config.service";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  providers: [ConfigService],
  styleUrls: ['./app.component.less']
})
export class AppComponent implements OnInit {
  title = 'PWGuard';
  config = null;

  constructor(private configService: ConfigService) {}

  ngOnInit(): void {
    this
      .configService
      .getConfig()
      .then(config => {
        console.log("Got config:", config);
        this.config = config;
      })
  }
}
