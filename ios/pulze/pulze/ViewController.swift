//
//  ViewController.swift
//  pulze
//
//  Created by Lars Nielsen on 19/01/2017.
//  Copyright © 2017 steinwurf. All rights reserved.
//

import UIKit

class ViewController: UIViewController {

    let PORT = 51423
    let GROUPNAME = "224.0.0.251"
    
    @IBOutlet weak var lastPacketNumberLabel: UILabel!
    
    @IBOutlet weak var lostPacketsLabel: UILabel!
    
    @IBOutlet weak var lostPacketsPercentLabel: UILabel!
    
    @IBOutlet weak var packetCountLabel: UILabel!
    
    @IBOutlet weak var keepAliveIntervalLabel: UILabel!
    
    
    // TODO: make text fields
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // Do any additional setup after loading the view, typically from a nib.
    }

    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }


}

